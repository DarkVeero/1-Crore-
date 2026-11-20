#!/usr/bin/env node
'use strict';

/**
 * Free-tier Scheduled + Targeted Announcement Dispatcher for 1 Crore.
 *
 * Designed to run on a free schedule (e.g. GitHub Actions cron every 1-5 minutes,
 * local cron, or Firebase Admin runner) with zero paid Cloud Functions required.
 *
 * Usage:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json \
 *     node scripts/dispatch_scheduled_announcements.js [--dry-run]
 *
 * Optional env:
 *   FIREBASE_SERVICE_ACCOUNT_JSON  Inline JSON string of Firebase service account
 *   FIRESTORE_DATABASE_ID          Defaults to ai-studio-android-1crore-0f5f169b-b940-4c2e-bd8a-4ec790c98827
 *   FCM_ANNOUNCEMENTS_TOPIC        Defaults to "announcements"
 */

const DEFAULT_DATABASE_ID = 'ai-studio-android-1crore-0f5f169b-b940-4c2e-bd8a-4ec790c98827';
const DEFAULT_TOPIC = 'announcements';
const MILESTONE_THRESHOLDS = [100000, 1000000];
const THIRTY_DAYS_MILLIS = 30 * 24 * 60 * 60 * 1000;

function resolveTimestampMillis(raw, fallbackMillis = 0) {
  if (raw === null || raw === undefined) return fallbackMillis;
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return raw >= 1e9 && raw < 1e10 ? raw * 1000 : raw;
  }
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    if (!trimmed) return fallbackMillis;
    const asNum = Number(trimmed);
    if (Number.isFinite(asNum)) {
      return asNum >= 1e9 && asNum < 1e10 ? asNum * 1000 : asNum;
    }
    const parsed = Date.parse(trimmed);
    return Number.isFinite(parsed) ? parsed : fallbackMillis;
  }
  if (typeof raw === 'object') {
    if (typeof raw.toMillis === 'function') {
      return raw.toMillis();
    }
    const seconds = raw.seconds ?? raw._seconds;
    if (typeof seconds === 'number' && Number.isFinite(seconds)) {
      const nanos = Number(raw.nanoseconds ?? raw._nanoseconds ?? 0);
      return seconds * 1000 + Math.floor(nanos / 1e6);
    }
  }
  return fallbackMillis;
}

function isAnnouncementDue(data, nowMillis = Date.now()) {
  if (!data || data.active === false) return false;
  if (data.dispatched === true) return false;

  const scheduledAtMillis = resolveTimestampMillis(
    data.scheduledAt ?? data.scheduledAtMillis ?? data.scheduled_at,
    0
  );
  const startAtMillis = resolveTimestampMillis(
    data.startAt ?? data.startAtMillis ?? data.start_at,
    scheduledAtMillis
  );
  const effectiveStart = Math.max(scheduledAtMillis, startAtMillis);
  if (effectiveStart > nowMillis) return false;

  const endAtMillis = resolveTimestampMillis(
    data.endAt ?? data.endAtMillis ?? data.end_at,
    Number.MAX_SAFE_INTEGER
  );
  if (nowMillis > endAtMillis) return false;

  const title = String(data.title || '').trim();
  const body = String(data.body || data.message || '').trim();
  return Boolean(title || body);
}

function parseUrgentFlag(raw) {
  if (typeof raw === 'boolean') return raw;
  if (typeof raw === 'number') return raw !== 0;
  if (typeof raw === 'string') {
    const clean = raw.trim().toLowerCase();
    return clean === 'true' || clean === '1' || clean === 'yes' || clean === 'on';
  }
  return false;
}

function shouldBypassFrequencyCaps(data) {
  if (!data || typeof data !== 'object') return false;
  return parseUrgentFlag(data.isUrgent ?? data.urgent ?? data.is_urgent);
}

function buildFcmTopicPayload(docId, data, topic = DEFAULT_TOPIC) {
  const button = data.button && typeof data.button === 'object' ? data.button : {};
  const title = String(data.title || '').trim();
  const body = String(data.body || data.message || '').trim();
  const imageUrl = String(data.imageUrl || data.image_url || '').trim();
  const buttonText = String(button.text || data.buttonText || '').trim();
  const buttonAction = String(button.action || data.buttonAction || '').trim();
  const buttonUrl = String(button.url || data.buttonUrl || data.url || data.link || '').trim();

  const scheduledAtMillis = resolveTimestampMillis(
    data.scheduledAt ?? data.scheduledAtMillis ?? data.scheduled_at,
    0
  );
  const startAtMillis = resolveTimestampMillis(
    data.startAt ?? data.startAtMillis ?? data.start_at,
    scheduledAtMillis
  );
  const endAtMillis = resolveTimestampMillis(
    data.endAt ?? data.endAtMillis ?? data.end_at,
    0
  );

  const targetAudience = String(
    data.targetAudience || data.target_audience || (data.targeting && data.targeting.type) || 'all'
  ).trim() || 'all';
  const inactiveDays = String(
    data.inactiveDays ?? data.inactiveDaysThreshold ?? (data.targeting && data.targeting.inactiveDays) ?? 3
  );
  const isUrgent = shouldBypassFrequencyCaps(data);

  // Send high-priority data payload so AnnouncementMessagingService always runs
  // (foreground & background) to enforce scheduledAt, targetAudience, and frequency caps
  // (or bypass all caps immediately when isUrgent is true).
  const payloadData = {
    type: 'announcement',
    id: docId,
    announcementId: docId,
    title,
    body,
    targetAudience,
    inactiveDays,
    isUrgent: isUrgent ? 'true' : 'false',
    scheduledAtMillis: String(scheduledAtMillis || 0),
    startAtMillis: String(startAtMillis || scheduledAtMillis || 0),
    priority: String(data.priority ?? (isUrgent ? 100 : 0)),
    maxShowsPerDay: String(data.maxShowsPerDay ?? 3),
    maxShowsPerWeek: String(data.maxShowsPerWeek ?? 21),
  };

  if (endAtMillis > 0) {
    payloadData.endAtMillis = String(endAtMillis);
  }
  if (imageUrl) {
    payloadData.imageUrl = imageUrl;
  }
  if (buttonUrl) {
    payloadData.buttonUrl = buttonUrl;
    payloadData.buttonText = buttonText || 'Join';
    payloadData.buttonAction = buttonAction || 'link';
  }

  return {
    topic,
    android: {
      priority: 'high',
    },
    data: payloadData,
  };
}

function initFirebaseAdmin() {
  const admin = require('firebase-admin');
  const { getFirestore } = require('firebase-admin/firestore');

  if (!admin.apps.length) {
    if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
      const certObj = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
      admin.initializeApp({
        credential: admin.credential.cert(certObj),
      });
    } else {
      admin.initializeApp({
        credential: admin.credential.applicationDefault(),
      });
    }
  }

  const databaseId = process.env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID;
  const db = getFirestore(admin.app(), databaseId);
  return { admin, db };
}

function parseSentMilestones(raw) {
  if (!raw) return new Set();
  const entries = Array.isArray(raw)
    ? raw
    : typeof raw === 'object'
      ? Object.keys(raw).filter((k) => raw[k] !== false && raw[k] !== null)
      : [];
  const out = new Set();
  for (const entry of entries) {
    const cleaned = typeof entry === 'string'
      ? entry.trim().replace(/^milestone_/, '').replace(/,/g, '')
      : entry;
    const num = Number(cleaned);
    if (Number.isFinite(num) && num > 0) {
      out.add(Math.trunc(num));
    }
  }
  return out;
}

function getPendingMilestones(statsData) {
  if (!statsData || typeof statsData !== 'object') return [];
  const totalRaw = typeof statsData.totalCount === 'string'
    ? Number(statsData.totalCount.replace(/,/g, ''))
    : Number(statsData.totalCount);
  if (!Number.isFinite(totalRaw) || totalRaw <= 0) return [];
  const sent = parseSentMilestones(
    statsData.sentMilestones ?? statsData.milestonesSent ?? statsData.milestones
  );
  return MILESTONE_THRESHOLDS.filter((threshold) => totalRaw >= threshold && !sent.has(threshold));
}

function buildMilestoneAnnouncementDoc(threshold, nowMillis = Date.now(), statsData = null) {
  const isTenLakh = threshold >= 1000000;
  const title = isTenLakh
    ? '10 Lakh Durood Milestone! ✨'
    : '1 Lakh Durood Milestone! 🌙';
  const body = isTenLakh
    ? 'MashaAllah! Our community together has crossed 1,000,000 Durood Pak toward our 1 Crore goal!'
    : 'Alhamdulillah! Everyone together has crossed 100,000 Durood Pak. Keep reciting to reach 1 Crore!';

  const rawLinkUrl = String(
    (statsData && statsData.milestoneLinks && statsData.milestoneLinks[String(threshold)]) ||
    (statsData && statsData.milestoneLinkUrl) ||
    process.env.MILESTONE_LINK_URL ||
    ''
  ).trim();
  const hasValidLink = /^https?:\/\//i.test(rawLinkUrl);

  const doc = {
    id: `milestone_${threshold}`,
    active: true,
    title,
    body,
    startAtMillis: nowMillis,
    endAtMillis: nowMillis + THIRTY_DAYS_MILLIS,
    maxShowsPerDay: 1,
    maxShowsPerWeek: 1,
    priority: isTenLakh ? 21 : 20,
    targetAudience: 'all',
    inactiveDays: 3,
    milestoneThreshold: threshold,
    dispatched: false,
  };

  if (hasValidLink) {
    doc.buttonText = 'Join';
    doc.buttonAction = 'link';
    doc.buttonUrl = rawLinkUrl;
    doc.button = {
      text: 'Join',
      action: 'link',
      url: rawLinkUrl,
    };
  }
  return doc;
}

async function dispatchGlobalMilestones({ admin, db, topic, dryRun = false, nowMillis = Date.now() }) {
  const globalRef = db.collection('stats').doc('global');
  const globalSnap = await globalRef.get();
  if (!globalSnap.exists) {
    return 0;
  }
  const statsData = globalSnap.data() || {};
  const pending = getPendingMilestones(statsData);
  if (pending.length === 0) {
    return 0;
  }

  let milestoneDispatched = 0;
  for (const threshold of pending) {
    const docId = `milestone_${threshold}`;
    const annRef = db.collection('announcements').doc(docId);
    const existingSnap = await annRef.get();
    const docData = existingSnap.exists
      ? { ...buildMilestoneAnnouncementDoc(threshold, nowMillis, statsData), ...existingSnap.data() }
      : buildMilestoneAnnouncementDoc(threshold, nowMillis, statsData);

    if (docData.dispatched === true) {
      if (!dryRun) {
        await globalRef.update({
          sentMilestones: admin.firestore.FieldValue.arrayUnion(threshold),
        });
      }
      continue;
    }

    const message = buildFcmTopicPayload(docId, docData, topic);
    if (dryRun) {
      console.log(`[DRY-RUN] Would dispatch milestone ${docId}:`, JSON.stringify(message));
      milestoneDispatched++;
      continue;
    }

    const fcmMessageId = await admin.messaging().send(message);
    await annRef.set({
      ...docData,
      startAt: existingSnap.exists && docData.startAt ? docData.startAt : new Date(docData.startAtMillis || nowMillis),
      endAt: existingSnap.exists && docData.endAt ? docData.endAt : new Date(docData.endAtMillis || (nowMillis + THIRTY_DAYS_MILLIS)),
      dispatched: true,
      dispatchedAt: admin.firestore.FieldValue.serverTimestamp(),
      fcmMessageId,
    }, { merge: true });
    await globalRef.update({
      sentMilestones: admin.firestore.FieldValue.arrayUnion(threshold),
    });
    console.log(`Dispatched milestone ${docId} -> topic "${topic}" (messageId=${fcmMessageId})`);
    milestoneDispatched++;
  }
  return milestoneDispatched;
}

async function runDispatch({ dryRun = false, nowMillis = Date.now() } = {}) {
  const topic = process.env.FCM_ANNOUNCEMENTS_TOPIC || DEFAULT_TOPIC;
  const { admin, db } = initFirebaseAdmin();

  const milestoneDispatched = await dispatchGlobalMilestones({
    admin,
    db,
    topic,
    dryRun,
    nowMillis,
  });

  const snap = await db.collection('announcements').where('active', '==', true).get();

  const dueDocs = snap.docs
    .filter((doc) => isAnnouncementDue(doc.data(), nowMillis))
    .sort((a, b) => {
      const aUrgent = shouldBypassFrequencyCaps(a.data()) ? 1 : 0;
      const bUrgent = shouldBypassFrequencyCaps(b.data()) ? 1 : 0;
      if (bUrgent !== aUrgent) return bUrgent - aUrgent;
      const aPriority = Number(a.data().priority || 0);
      const bPriority = Number(b.data().priority || 0);
      return bPriority - aPriority;
    });

  let dispatchedCount = milestoneDispatched;
  for (const doc of dueDocs) {
    const data = doc.data();
    const message = buildFcmTopicPayload(doc.id, data, topic);
    if (dryRun) {
      console.log(`[DRY-RUN] Would dispatch announcement ${doc.id}:`, JSON.stringify(message));
      dispatchedCount++;
      continue;
    }

    const fcmMessageId = await admin.messaging().send(message);
    await doc.ref.update({
      dispatched: true,
      dispatchedAt: admin.firestore.FieldValue.serverTimestamp(),
      fcmMessageId,
    });
    console.log(`Dispatched announcement ${doc.id} -> topic "${topic}" (messageId=${fcmMessageId})`);
    dispatchedCount++;
  }

  console.log(`Finished scheduled & milestone announcement check. Dispatched: ${dispatchedCount}`);
  return dispatchedCount;
}

if (require.main === module) {
  const dryRun = process.argv.includes('--dry-run');
  runDispatch({ dryRun }).catch((err) => {
    console.error('Dispatch failed:', err);
    process.exitCode = 1;
  });
}

module.exports = {
  DEFAULT_DATABASE_ID,
  DEFAULT_TOPIC,
  MILESTONE_THRESHOLDS,
  resolveTimestampMillis,
  isAnnouncementDue,
  parseUrgentFlag,
  shouldBypassFrequencyCaps,
  buildFcmTopicPayload,
  parseSentMilestones,
  getPendingMilestones,
  buildMilestoneAnnouncementDoc,
  dispatchGlobalMilestones,
  runDispatch,
};
