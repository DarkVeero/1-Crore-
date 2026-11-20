#!/usr/bin/env node
'use strict';

/**
 * Companion server-side Group Target Aggregator for 1 Crore.
 *
 * Computes authoritative serverTotalCount = sum of members' main Durood lifetime totals
 * from users/{uid}/daily_counts, ranks members most-read first, computes weekly Top-3
 * readers, and records 1 Lakh / 1 Crore group target celebrations on groups/{groupId}.
 */

const DEFAULT_DATABASE_ID = 'ai-studio-android-1crore-0f5f169b-b940-4c2e-bd8a-4ec790c98827';
const TARGET_ONE_LAKH = 100000;
const TARGET_ONE_CRORE = 10000000;

function formatDateKeyUtc(date) {
  return date.toISOString().slice(0, 10);
}

function getLast7DateKeys(today = new Date()) {
  const keys = new Set();
  for (let i = 0; i < 7; i++) {
    const d = new Date(today.getTime() - i * 86400000);
    keys.add(formatDateKeyUtc(d));
  }
  return keys;
}

function aggregateMainGroupMembers(memberDocs, todayKey, last7Keys) {
  const ranked = memberDocs.map((m) => {
    const dailyEntries = Array.isArray(m.dailyEntries) ? m.dailyEntries : [];
    let lifetimeTotalCount = 0;
    let todayCount = 0;
    let weeklyCount = 0;

    for (const day of dailyEntries) {
      const c = Math.max(0, Number(day.count || 0));
      lifetimeTotalCount += c;
      if (day.dateKey === todayKey) todayCount += c;
      if (last7Keys.has(day.dateKey)) weeklyCount += c;
    }

    return {
      userId: String(m.userId),
      displayName: String(m.displayName || 'Friend'),
      todayCount,
      todayDateKey: todayKey,
      lifetimeTotalCount,
      weeklyCount,
      updatedAtMillis: Date.now(),
    };
  });

  ranked.sort((a, b) => {
    if (b.lifetimeTotalCount !== a.lifetimeTotalCount) {
      return b.lifetimeTotalCount - a.lifetimeTotalCount;
    }
    if (b.todayCount !== a.todayCount) {
      return b.todayCount - a.todayCount;
    }
    return a.displayName.localeCompare(b.displayName);
  });

  const serverTotalCount = ranked.reduce((acc, m) => acc + m.lifetimeTotalCount, 0);
  const weeklyTop3 = ranked
    .filter((m) => m.weeklyCount > 0)
    .sort((a, b) => b.weeklyCount - a.weeklyCount || b.lifetimeTotalCount - a.lifetimeTotalCount)
    .slice(0, 3)
    .map((m, idx) => ({
      rank: idx + 1,
      userId: m.userId,
      displayName: m.displayName,
      weeklyCount: m.weeklyCount,
    }));

  const celebrations = [];
  if (serverTotalCount >= TARGET_ONE_LAKH) celebrations.push('TARGET_1_LAKH');
  if (serverTotalCount >= TARGET_ONE_CRORE) celebrations.push('TARGET_1_CRORE');

  return {
    members: ranked,
    serverTotalCount,
    weeklyTop3,
    celebrations,
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

  const dbId = process.env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID;
  const db = getFirestore(admin.app(), dbId);
  return { admin, db };
}

async function run({ dryRun = false } = {}) {
  const { db } = initFirebaseAdmin();
  const today = new Date();
  const todayKey = formatDateKeyUtc(today);
  const last7Keys = getLast7DateKeys(today);

  const groupsSnap = await db.collection('groups').get();
  let updatedCount = 0;

  for (const groupDoc of groupsSnap.docs) {
    const data = groupDoc.data() || {};
    if (data.type === 'custom_zikr') {
      continue;
    }
    const existingMembers = Array.isArray(data.members) ? data.members : [];
    const memberIds = Array.isArray(data.memberIds) && data.memberIds.length
      ? data.memberIds
      : existingMembers.map((m) => m.userId).filter(Boolean);

    const fetchedMembers = [];
    for (const uid of memberIds) {
      const profileSnap = await db.collection('users').doc(uid).get();
      const profileName = profileSnap.exists ? profileSnap.data()?.displayName : null;
      const existingName = existingMembers.find((m) => m.userId === uid)?.displayName;
      const dailySnap = await db.collection('users').doc(uid).collection('daily_counts').get();
      const dailyEntries = dailySnap.docs.map((d) => ({
        dateKey: d.id,
        count: Number(d.data()?.count || 0),
      }));
      fetchedMembers.push({
        userId: uid,
        displayName: profileName || existingName || 'Friend',
        dailyEntries,
      });
    }

    const aggregated = aggregateMainGroupMembers(fetchedMembers, todayKey, last7Keys);
    if (!dryRun) {
      await groupDoc.ref.set(
        {
          members: aggregated.members,
          memberIds: aggregated.members.map((m) => m.userId),
          serverTotalCount: aggregated.serverTotalCount,
          weeklyTop3: aggregated.weeklyTop3,
          celebrations: aggregated.celebrations,
          updatedAtMillis: Date.now(),
        },
        { merge: true }
      );
    }
    updatedCount += 1;
  }

  console.log(`Aggregated ${updatedCount} main Durood groups.`);
  return { updatedCount };
}

if (require.main === module) {
  const dryRun = process.argv.includes('--dry-run');
  run({ dryRun }).catch((err) => {
    console.error('Group aggregation failed:', err);
    process.exit(1);
  });
}

module.exports = {
  aggregateMainGroupMembers,
  run,
};
