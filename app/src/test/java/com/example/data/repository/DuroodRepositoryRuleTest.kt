package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.DuroodRepository
import com.example.data.local.AppDatabase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DuroodRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var localDb: AppDatabase

    @Before
    fun setUpLocalDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        localDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeLocalDb() {
        if (::localDb.isInitialized) {
            localDb.close()
        }
    }

    @Test
    fun syncWithCloud_ownerProfileAndCounts_syncsAndNeverOverwritesWithSmallerCloudNumber() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = DuroodRepository(localDb, firestore, auth)

        val dateKey = "2026-11-14"
        repository.ensureLocalProfile(uid, "Alice")
        repository.completeSetup(uid, "Alice", 2600)
        repository.incrementCount(uid, dateKey, 500L)

        val firstSync = withTimeout(DEFAULT_TIMEOUT_MS) { repository.syncWithCloud() }
        assertTrue(firstSync.isSuccess)

        val cloudDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid)
                .collection("daily_counts").document(dateKey)
                .get().await()
        }
        assertEquals(500L, cloudDoc.getLong("count"))

        // Increment locally offline while cloud still holds 500; sync must push 1200, never revert to 500
        repository.incrementCount(uid, dateKey, 700L)
        val secondSync = withTimeout(DEFAULT_TIMEOUT_MS) { repository.syncWithCloud() }
        assertTrue(secondSync.isSuccess)

        val localAfter = repository.getDayCountOnce(uid, dateKey)
        assertEquals(1200L, localAfter)

        val cloudAfter = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid)
                .collection("daily_counts").document(dateKey)
                .get().await()
        }
        assertEquals(1200L, cloudAfter.getLong("count"))
    }

    @Test
    fun syncWithCloud_customCounters_syncsAndNeverOverwritesWithSmallerCloudNumber() = runBlocking {
        val uid = signInTestUser("custom_${UUID.randomUUID()}@test.com")
        val repository = DuroodRepository(localDb, firestore, auth)

        repository.ensureLocalProfile(uid, "Bilal")
        repository.completeSetup(uid, "Bilal", 2600)
        val counterId = repository.createCustomCounter(uid, "Morning Istighfar", 3300L)
        repository.incrementCustomCounter(uid, counterId, 45L)

        val firstSync = withTimeout(DEFAULT_TIMEOUT_MS) { repository.syncWithCloud() }
        assertTrue(firstSync.isSuccess)

        val cloudDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid)
                .collection("custom_counters").document(counterId)
                .get().await()
        }
        assertEquals("Morning Istighfar", cloudDoc.getString("name"))
        assertEquals(3300L, cloudDoc.getLong("targetCount"))
        assertEquals(45L, cloudDoc.getLong("currentCount"))

        // Increment locally offline while cloud holds 45; sync must push 120, never revert to 45
        repository.incrementCustomCounter(uid, counterId, 75L)
        val secondSync = withTimeout(DEFAULT_TIMEOUT_MS) { repository.syncWithCloud() }
        assertTrue(secondSync.isSuccess)

        val activeCounters = repository.observeCustomCounters(uid).first()
        assertEquals(1, activeCounters.size)
        assertEquals(120L, activeCounters.first().currentCount)

        val cloudAfter = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid)
                .collection("custom_counters").document(counterId)
                .get().await()
        }
        assertEquals(120L, cloudAfter.getLong("currentCount"))
    }

    @Test
    fun crossUserRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID()}@test.com")
        val aliceRepo = DuroodRepository(localDb, firestore, auth)
        aliceRepo.ensureLocalProfile(aliceUid, "Alice")
        aliceRepo.completeSetup(aliceUid, "Alice", 2600)
        aliceRepo.incrementCount(aliceUid, "2026-11-14", 100L)
        val aliceCounterId = aliceRepo.createCustomCounter(aliceUid, "Evening Tasbih", 500L)
        aliceRepo.incrementCustomCounter(aliceUid, aliceCounterId, 33L)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.syncWithCloud() }.getOrThrow()

        // Switch to Bob and try reading Alice's Firestore counts and custom counters
        signInTestUser("bob_iso_${UUID.randomUUID()}@test.com")
        var caughtDaily: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("daily_counts").get().await()
            }
        } catch (e: Exception) {
            caughtDaily = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caughtDaily?.code)

        var caughtCustom: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("custom_counters").get().await()
            }
        } catch (e: Exception) {
            caughtCustom = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caughtCustom?.code)
    }

    @Test
    fun unauthenticatedRead_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = DuroodRepository(localDb, firestore, auth)

        val syncResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.syncWithCloud() }
        assertTrue(syncResult.isFailure)

        var caught: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document("some_uid")
                    .collection("daily_counts").get().await()
            }
        } catch (e: Exception) {
            caught = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
    }

    @Test
    fun backupExportAndRestore_preservesHigherLocalCounts() = runBlocking {
        val uid = signInTestUser("backup_${UUID.randomUUID()}@test.com")
        val repository = DuroodRepository(localDb, firestore, auth)
        repository.ensureLocalProfile(uid, "Zain")
        repository.completeSetup(uid, "Zain", 2600)
        repository.incrementCount(uid, "2026-11-10", 1000L)
        repository.incrementCount(uid, "2026-11-11", 500L)
        val counterId = repository.createCustomCounter(uid, "Special Goal", 1000L)
        repository.incrementCustomCounter(uid, counterId, 250L)

        val backupJson = repository.exportBackupJson(uid)

        // Increase local counts before restoring older backup
        repository.incrementCount(uid, "2026-11-11", 1500L)
        repository.incrementCustomCounter(uid, counterId, 300L)
        val restoreResult = repository.restoreBackupJson(uid, backupJson)
        assertTrue(restoreResult.isSuccess)

        assertEquals(1000L, repository.getDayCountOnce(uid, "2026-11-10"))
        assertEquals(2000L, repository.getDayCountOnce(uid, "2026-11-11"))
        assertEquals(3000L, repository.observeTotalCount(uid).first())
        val restoredCounter = repository.observeCustomCounter(counterId).first()
        assertEquals(550L, restoredCounter?.currentCount)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
