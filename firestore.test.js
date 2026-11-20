const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfile(userId) {
  const ts = pastTimestamp();
  return {
    userId,
    displayName: "Amina Tariq",
    dailyTarget: 2600,
    setupCompleted: true,
    reminder1Hour: 18,
    reminder1Minute: 0,
    reminder2Hour: 21,
    reminder2Minute: 0,
    remindersEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validDailyCount(userId, dateKey, count = 2600) {
  const ts = pastTimestamp();
  return {
    userId,
    dateKey,
    count,
    clientUpdatedAt: Date.now(),
    createdAt: ts,
    updatedAt: ts,
  };
}

function validCustomCounter(userId, name = "Morning Tasbih", targetCount = 1000, currentCount = 0) {
  const ts = pastTimestamp();
  return {
    userId,
    name,
    targetCount,
    currentCount,
    clientUpdatedAt: Date.now(),
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user profile, daily counts, or custom counters", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("daily_counts").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("custom_counters").get());
});

test("Authenticated owner can create, read, update, and delete own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(profileRef.update({ dailyTarget: 3000, updatedAt: pastTimestamp() }));
  await assertSucceeds(profileRef.delete());
});

test("Authenticated user cannot read, write, or list another user's profile, daily counts, or custom counters", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).set(validProfile(BOB_UID));
    await db.collection("users").doc(BOB_UID).collection("daily_counts").doc("2026-11-14").set(validDailyCount(BOB_UID, "2026-11-14", 500));
    await db.collection("users").doc(BOB_UID).collection("custom_counters").doc("counter_1").set(validCustomCounter(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).set(validProfile(BOB_UID)));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("daily_counts").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("daily_counts").doc("2026-11-14").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("custom_counters").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("custom_counters").doc("counter_1").get());
});

test("Shadow update with ghost field or identity spoofing is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfile(ALICE_UID),
      isAdmin: true,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(BOB_UID))
  );
});

test("Authenticated owner can create, update, list, and delete valid daily counts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("daily_counts");
  const docRef = col.doc("2026-11-14");

  await assertSucceeds(docRef.set(validDailyCount(ALICE_UID, "2026-11-14", 1200)));
  await assertSucceeds(col.get());
  await assertSucceeds(docRef.update({ count: 2600, clientUpdatedAt: Date.now(), updatedAt: pastTimestamp() }));
  await assertSucceeds(docRef.delete());
});

test("Authenticated owner can create, update, list, and delete valid custom counters", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("custom_counters");
  const docRef = col.doc("counter_1");

  await assertSucceeds(docRef.set(validCustomCounter(ALICE_UID, "Istighfar", 3300, 120)));
  await assertSucceeds(col.get());
  await assertSucceeds(docRef.update({ currentCount: 500, clientUpdatedAt: Date.now(), updatedAt: pastTimestamp() }));
  await assertSucceeds(docRef.delete());
});

test("Invalid dateKey format, negative count, or invalid custom counter is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("daily_counts");
  const customCol = aliceDb.collection("users").doc(ALICE_UID).collection("custom_counters");

  await assertFails(col.doc("invalid-date").set(validDailyCount(ALICE_UID, "invalid-date", 100)));
  await assertFails(col.doc("2026-11-14").set(validDailyCount(ALICE_UID, "2026-11-14", -5)));
  await assertFails(customCol.doc("c1").set(validCustomCounter(ALICE_UID, "", 100, 0)));
  await assertFails(customCol.doc("c2").set(validCustomCounter(ALICE_UID, "Valid", 0, 0)));
});

test("Signed-in users can read stats/global, unauthenticated users cannot read it, and no client can list or write it", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("stats").doc("global").set({ totalCount: 1500000, userCount: 120 });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("stats").doc("global").get());
  await assertFails(unauthDb.collection("stats").get());
  await assertFails(unauthDb.collection("stats").doc("global").set({ totalCount: 999, userCount: 1 }));

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("stats").doc("global").get());
  await assertFails(aliceDb.collection("stats").get());
  await assertFails(aliceDb.collection("stats").doc("global").set({ totalCount: 999, userCount: 1 }));
  await assertFails(aliceDb.collection("stats").doc("global").update({ totalCount: 2000000 }));
  await assertFails(aliceDb.collection("stats").doc("global").delete());
});

test("Signed-in users can get and list announcements, unauthenticated users cannot, invalid IDs fail, and all client writes are denied", async () => {
  const now = Date.now();
  const sampleAnnouncement = {
    active: true,
    title: "Assalamu Alaikum",
    body: "Join the official channel.",
    button: {
      text: "Join my channel",
      action: "link",
      url: "https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B",
    },
    startAt: new Date(now - 60000),
    endAt: new Date(now + 86400000),
    maxShowsPerDay: 3,
    maxShowsPerWeek: 21,
    priority: 10,
  };

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("announcements").doc("test-join-2").set(sampleAnnouncement);
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("announcements").get());
  await assertFails(unauthDb.collection("announcements").doc("test-join-2").get());
  await assertFails(unauthDb.collection("announcements").doc("new-doc").set(sampleAnnouncement));

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("announcements").get());
  await assertSucceeds(aliceDb.collection("announcements").doc("test-join-2").get());
  await assertFails(aliceDb.collection("announcements").doc("invalid id!").get());
  await assertFails(aliceDb.collection("announcements").doc("test-join-2").set(sampleAnnouncement));
  await assertFails(aliceDb.collection("announcements").doc("test-join-2").update({ active: false }));
  await assertFails(aliceDb.collection("announcements").doc("test-join-2").delete());
  await assertFails(aliceDb.collection("announcements").doc("new-doc").set(sampleAnnouncement));
});


