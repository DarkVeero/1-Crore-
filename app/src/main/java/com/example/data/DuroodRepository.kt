package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.announcements.AnnouncementItem
import com.example.announcements.AnnouncementParser
import com.example.data.local.AppDatabase
import com.example.data.local.CustomCounterEntity
import com.example.data.local.DailyCountEntity
import com.example.data.local.UserProfileEntity
import com.example.data.remote.FirestoreCustomCounter
import com.example.data.remote.FirestoreDailyCount
import com.example.data.remote.FirestoreUserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeoutException

class DuroodRepository(
    private val localDb: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        localDb = AppDatabase.getInstance(context),
        firestore = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private val profileDao = localDb.userProfileDao()
    private val dailyDao = localDb.dailyCountDao()
    private val customDao = localDb.customCounterDao()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<UserProfileEntity?> = profileDao.observeProfile(userId)

    fun observeAllDays(userId: String): Flow<List<DailyCountEntity>> = dailyDao.observeAllDays(userId)
fun observeRecentDays(userId: String, limit: Int): Flow<List<DailyCountEntity>> = dailyDao.observeRecentDays(userId, limit)

    fun observeTotalCount(userId: String): Flow<Long> = dailyDao.observeTotalCount(userId)

    fun observeDayCount(userId: String, dateKey: String): Flow<Long> = dailyDao.observeDayCount(userId, dateKey)

    fun observeHasPendingSync(userId: String): Flow<Boolean> = dailyDao.observeHasPendingSync(userId)

    fun observePendingDaysCount(userId: String): Flow<Int> = dailyDao.observePendingDaysCount(userId)

    fun observePendingDuroodTotal(userId: String): Flow<Long> = dailyDao.observePendingDuroodTotal(userId)

    fun observeCustomCounters(userId: String): Flow<List<CustomCounterEntity>> =
        customDao.observeActiveCounters(userId)

    fun observeCustomCounter(counterId: String): Flow<CustomCounterEntity?> =
        customDao.observeCounter(counterId)

    suspend fun getProfileOnce(userId: String): UserProfileEntity? = profileDao.getProfile(userId)

    suspend fun getDayCountOnce(userId: String, dateKey: String): Long =
        dailyDao.getDay(userId, dateKey)?.count ?: 0L

    suspend fun hasLocalExistingData(userId: String): Boolean {
        val profile = profileDao.getProfile(userId)
        if (profile != null && profile.setupCompleted) return true
        if (dailyDao.getAllDays(userId).isNotEmpty()) return true
        if (customDao.getActiveCounters(userId).isNotEmpty()) return true
        return false
    }

    suspend fun ensureLocalProfile(userId: String, fallbackName: String) {
        val existing = profileDao.getProfile(userId)
        if (existing == null) {
            val cleanName = fallbackName.trim().ifBlank { "Friend" }.take(80)
            profileDao.upsertProfile(
                UserProfileEntity(
                    userId = userId,
                    displayName = cleanName,
                    dailyTarget = DEFAULT_DAILY_TARGET,
                    setupCompleted = false,
                    pendingSync = false,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun ensureExistingAccountSetupCompleted(
        userId: String,
        fallbackName: String,
        accountCreatedAtMillis: Long = 0L
    ) {
        val existing = profileDao.getProfile(userId)
        val earliestDayMillis = getEarliestLocalDayMillis(userId)
        val now = System.currentTimeMillis()
        val preservedStartedAt = listOfNotNull(
            existing?.startedAtMillis?.takeIf { it > 0L },
            earliestDayMillis?.takeIf { it > 0L },
            accountCreatedAtMillis.takeIf { it > 0L }
        ).minOrNull() ?: now

        if (existing != null && existing.setupCompleted) {
            if (existing.startedAtMillis <= 0L || preservedStartedAt < existing.startedAtMillis) {
                profileDao.upsertProfile(existing.copy(startedAtMillis = preservedStartedAt))
            }
            return
        }

        val cleanName = existing?.displayName?.takeIf { it.isNotBlank() && it != "Friend" }
            ?: fallbackName.trim().ifBlank { existing?.displayName ?: "Friend" }.ifBlank { "Friend" }.take(80)
        val target = existing?.dailyTarget?.takeIf { it > 0 } ?: DEFAULT_DAILY_TARGET

        val updated = (existing ?: UserProfileEntity(userId = userId, displayName = cleanName)).copy(
            displayName = cleanName,
            dailyTarget = target.coerceIn(1, ONE_CRORE.toInt()),
            setupCompleted = true,
            startedAtMillis = preservedStartedAt,
            pendingSync = true,
            updatedAtMillis = now
        )
        profileDao.upsertProfile(updated)
    }

    suspend fun completeSetup(
        userId: String,
        displayName: String,
        dailyTarget: Int,
        fallbackStartedAtMillis: Long = 0L
    ) {
        val existing = profileDao.getProfile(userId)
        val cleanName = displayName.trim()
            .ifBlank { existing?.displayName.orEmpty() }
            .ifBlank { "Friend" }
            .take(80)
        val clampedTarget = dailyTarget.coerceIn(1, ONE_CRORE.toInt())
        val earliestDayMillis = getEarliestLocalDayMillis(userId)
        val now = System.currentTimeMillis()
        val preservedStartedAt = listOfNotNull(
            existing?.startedAtMillis?.takeIf { it > 0L },
            earliestDayMillis?.takeIf { it > 0L },
            fallbackStartedAtMillis.takeIf { it > 0L }
        ).minOrNull() ?: now

        val updated = (existing ?: UserProfileEntity(userId = userId, displayName = cleanName)).copy(
            displayName = cleanName,
            dailyTarget = clampedTarget,
            setupCompleted = true,
            startedAtMillis = preservedStartedAt,
            pendingSync = true,
            updatedAtMillis = now
        )
        profileDao.upsertProfile(updated)
    }

    private suspend fun getEarliestLocalDayMillis(userId: String): Long? {
        return dailyDao.getAllDays(userId).minOfOrNull { it.dateKey }?.let { key ->
            try {
                LocalDate.parse(key)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun updatePlan(userId: String, dailyTarget: Int) {
        val clampedTarget = dailyTarget.coerceIn(1, ONE_CRORE.toInt())
        val existing = profileDao.getProfile(userId) ?: return
        profileDao.upsertProfile(
            existing.copy(
                dailyTarget = clampedTarget,
                setupCompleted = true,
                pendingSync = true,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateDisplayName(userId: String, displayName: String) {
        val cleanName = displayName.trim().ifBlank { return }.take(80)
        val existing = profileDao.getProfile(userId) ?: return
        profileDao.upsertProfile(
            existing.copy(
                displayName = cleanName,
                pendingSync = true,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateReminders(
        userId: String,
        enabled: Boolean,
        r1Hour: Int,
        r1Minute: Int,
        r2Hour: Int,
        r2Minute: Int
    ) {
        val existing = profileDao.getProfile(userId) ?: return
        profileDao.upsertProfile(
            existing.copy(
                remindersEnabled = enabled,
                reminder1Hour = r1Hour.coerceIn(0, 23),
                reminder1Minute = r1Minute.coerceIn(0, 59),
                reminder2Hour = r2Hour.coerceIn(0, 23),
                reminder2Minute = r2Minute.coerceIn(0, 59),
                pendingSync = true,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateThemeMode(
        userId: String,
        themeMode: com.example.ui.theme.AppThemeMode,
        fallbackDisplayName: String = "Friend"
    ) {
        val now = System.currentTimeMillis()
        val existing = profileDao.getProfile(userId)
        if (existing != null) {
            profileDao.upsertProfile(
                existing.copy(
                    themeMode = themeMode.storageKey,
                    pendingSync = true,
                    updatedAtMillis = now
                )
            )
        } else {
            profileDao.upsertProfile(
                UserProfileEntity(
                    userId = userId,
                    displayName = fallbackDisplayName.trim().ifBlank { "Friend" }.take(80),
                    dailyTarget = DEFAULT_DAILY_TARGET,
                    setupCompleted = false,
                    pendingSync = true,
                    updatedAtMillis = now,
                    themeMode = themeMode.storageKey
                )
            )
        }
    }

    suspend fun incrementCount(userId: String, dateKey: String = LocalDate.now().toString(), delta: Long) {
        if (delta <= 0L) return
        dailyDao.incrementDay(userId, dateKey, delta, System.currentTimeMillis())
    }

    suspend fun setDayCount(userId: String, dateKey: String, newCount: Long) {
        dailyDao.setDayCount(userId, dateKey, newCount, System.currentTimeMillis())
    }

    suspend fun adjustDayCount(userId: String, dateKey: String, delta: Long) {
        dailyDao.adjustDay(userId, dateKey, delta, System.currentTimeMillis())
    }

    suspend fun createCustomCounter(userId: String, name: String, targetCount: Long): String {
        val cleanName = name.trim().take(80)
        require(cleanName.isNotEmpty()) { "Counter name cannot be empty." }
        val clampedTarget = targetCount.coerceIn(1L, 100_000_000L)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        customDao.upsertCounter(
            CustomCounterEntity(
                id = id,
                userId = userId,
                name = cleanName,
                targetCount = clampedTarget,
                currentCount = 0L,
                pendingSync = true,
                isManualEdit = false,
                isDeleted = false,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        return id
    }

    suspend fun incrementCustomCounter(userId: String, counterId: String, delta: Long) {
        if (delta <= 0L) return
        customDao.incrementCounter(userId, counterId, delta, System.currentTimeMillis())
    }

    suspend fun updateCustomCounterDetails(
        userId: String,
        counterId: String,
        name: String,
        targetCount: Long,
        currentCount: Long
    ) {
        val existing = customDao.getCounter(counterId) ?: return
        if (existing.userId != userId || existing.isDeleted) return
        val cleanName = name.trim().ifBlank { existing.name }.take(80)
        customDao.upsertCounter(
            existing.copy(
                name = cleanName,
                targetCount = targetCount.coerceIn(1L, 100_000_000L),
                currentCount = currentCount.coerceIn(0L, 100_000_000L),
                pendingSync = true,
                isManualEdit = currentCount != existing.currentCount,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteCustomCounter(userId: String, counterId: String) {
        val existing = customDao.getCounter(counterId) ?: return
        if (existing.userId != userId) return
        customDao.upsertCounter(
            existing.copy(
                isDeleted = true,
                pendingSync = true,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun exportBackupJson(userId: String): String {
        val profile = profileDao.getProfile(userId)
        val days = dailyDao.getAllDays(userId)
        val counters = customDao.getActiveCounters(userId)
        val encoded = BackupCodec.encode(profile, days, counters)
        // Restore-test gate on export: guarantee exported backup is valid and round-trip restorable
        BackupCodec.verifyBackupRoundTrip(encoded).getOrThrow()
        return encoded
    }

    data class RestoreOutcome(val restoredItems: Int, val skippedEntries: Int)

    suspend fun restoreBackupJson(userId: String, jsonText: String): Result<RestoreOutcome> {
        return try {
            // 1. Pre-restore verification gate: verify backup file decodes and round-trips cleanly
            val payload = BackupCodec.verifyBackupRoundTrip(jsonText).getOrElse { err ->
                return Result.failure(err)
            }
            val existingProfile = profileDao.getProfile(userId)
            val now = System.currentTimeMillis()
            val backupStarted = payload.startedAtMillis?.takeIf { it > 0L }
            val localStarted = existingProfile?.startedAtMillis?.takeIf { it > 0L }
            val earliestBackupDayMillis = payload.days.minOfOrNull { it.first }?.let { key ->
                try {
                    LocalDate.parse(key)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
            val earliestLocalDayMillis = getEarliestLocalDayMillis(userId)
            val resolvedStartedAt = listOfNotNull(
                backupStarted,
                localStarted,
                earliestBackupDayMillis?.takeIf { it > 0L },
                earliestLocalDayMillis?.takeIf { it > 0L }
            ).minOrNull() ?: now
            val mergedProfile = (existingProfile ?: UserProfileEntity(
                userId = userId,
                displayName = payload.displayName ?: "Friend"
            )).copy(
                displayName = payload.displayName ?: existingProfile?.displayName ?: "Friend",
                dailyTarget = payload.dailyTarget ?: existingProfile?.dailyTarget ?: DEFAULT_DAILY_TARGET,
                setupCompleted = true,
                reminder1Hour = payload.reminder1Hour ?: existingProfile?.reminder1Hour ?: 18,
                reminder1Minute = payload.reminder1Minute ?: existingProfile?.reminder1Minute ?: 0,
                reminder2Hour = payload.reminder2Hour ?: existingProfile?.reminder2Hour ?: 21,
                reminder2Minute = payload.reminder2Minute ?: existingProfile?.reminder2Minute ?: 0,
                remindersEnabled = payload.remindersEnabled ?: existingProfile?.remindersEnabled ?: true,
                startedAtMillis = resolvedStartedAt,
                pendingSync = true,
                updatedAtMillis = now,
                themeMode = payload.themeMode?.let {
                    com.example.ui.theme.AppThemeMode.fromStorageKey(it).storageKey
                } ?: existingProfile?.themeMode ?: com.example.ui.theme.AppThemeMode.BLACK_AND_WHITE.storageKey
            )
            profileDao.upsertProfile(mergedProfile)

            var restoredItems = 0
            for ((dateKey, backupCount) in payload.days) {
                val localExisting = dailyDao.getDay(userId, dateKey)
                val mergedCount = maxOf(localExisting?.count ?: 0L, backupCount)
                dailyDao.upsertDay(
                    DailyCountEntity(
                        userId = userId,
                        dateKey = dateKey,
                        count = mergedCount,
                        pendingSync = true,
                        isManualEdit = false,
                        updatedAtMillis = now
                    )
                )
                restoredItems++
            }
            for (counter in payload.customCounters) {
                val localExisting = customDao.getCounter(counter.id)
                if (localExisting?.isDeleted == true) continue
                val mergedCount = maxOf(localExisting?.currentCount ?: 0L, counter.currentCount)
                customDao.upsertCounter(
                    CustomCounterEntity(
                        id = counter.id,
                        userId = userId,
                        name = localExisting?.name ?: counter.name,
                        targetCount = localExisting?.targetCount ?: counter.targetCount,
                        currentCount = mergedCount,
                        pendingSync = true,
                        isManualEdit = false,
                        isDeleted = false,
                        createdAtMillis = localExisting?.createdAtMillis ?: now,
                        updatedAtMillis = now
                    )
                )
                restoredItems++
            }

            // 2. Post-restore verification gate: confirm Room state matches expected restored records and Started date
            val verifiedProfile = profileDao.getProfile(userId)
                ?: return Result.failure(IllegalStateException("Restore verification failed: profile missing after restore."))
            if (verifiedProfile.startedAtMillis != resolvedStartedAt) {
                return Result.failure(IllegalStateException("Restore verification failed: Started date was not preserved."))
            }
            for ((dateKey, backupCount) in payload.days) {
                val savedDay = dailyDao.getDay(userId, dateKey)
                if (savedDay == null || savedDay.count < backupCount) {
                    return Result.failure(IllegalStateException("Restore verification failed for $dateKey."))
                }
            }

            Result.success(RestoreOutcome(restoredItems, payload.droppedDays + payload.droppedCounters))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Synchronizes local Room state with Firestore safely:
     * - Never overwrites local counts with smaller cloud numbers.
     * - Pushes pending local counts, custom counters, & manual edits up to Firestore.
     * - Pulls larger cloud counts down to Room.
     */
    suspend fun syncWithCloud(): Result<Unit> = syncMutex.withLock {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val profileRef = firestore.collection("users").document(uid)
        val countsCollection = profileRef.collection("daily_counts")
        val customCollection = profileRef.collection("custom_counters")

        return try {
            // 1. Sync user profile
            val localProfile = profileDao.getProfile(uid)
            val cloudProfileSnap = try {
                profileRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, profileRef.path, auth)
            null
            }

            val cloudProfile = cloudProfileSnap?.toObject(FirestoreUserProfile::class.java)
            val rawCreatedAtMillis = runCatching { cloudProfileSnap?.getTimestamp("createdAt")?.toDate()?.time }.getOrNull()
                ?: cloudProfile?.createdAt?.toDate()?.time
                ?: 0L
            val rawStartDateMillis = runCatching { cloudProfileSnap?.getTimestamp("startDate")?.toDate()?.time }.getOrNull()
                ?: runCatching { cloudProfileSnap?.getLong("startDate") }.getOrNull()
                ?: runCatching { cloudProfileSnap?.getLong("startedAtMillis") }.getOrNull()
                ?: 0L
            val cloudStartedMillis = listOfNotNull(
                rawStartDateMillis.takeIf { it > 0L },
                rawCreatedAtMillis.takeIf { it > 0L }
            ).minOrNull() ?: 0L
            val rawDailyTarget = runCatching { cloudProfileSnap?.getLong("dailyTarget") }.getOrNull()
                ?: cloudProfile?.dailyTarget
                ?: 0L
            val rawSetupFlag = (runCatching { cloudProfileSnap?.getBoolean("setupCompleted") }.getOrNull() == true) ||
                (runCatching { cloudProfileSnap?.getBoolean("setupComplete") }.getOrNull() == true) ||
                (cloudProfile?.setupCompleted == true)
            val cloudDocHasSetup = (cloudProfileSnap?.exists() == true) && (
                rawSetupFlag ||
                    (rawDailyTarget > 0L && (cloudStartedMillis > 0L || cloudProfileSnap?.contains("dailyTarget") == true))
                )

            if (cloudProfileSnap != null && (cloudProfile == null || !cloudProfileSnap.exists())) {
                if (localProfile != null && localProfile.setupCompleted) {
                    val createPayload = mapOf(
                        "userId" to uid,
                        "displayName" to localProfile.displayName.ifBlank { "Friend" }.take(80),
                        "dailyTarget" to localProfile.dailyTarget.toLong(),
                        "setupCompleted" to true,
                        "reminder1Hour" to localProfile.reminder1Hour.toLong(),
                        "reminder1Minute" to localProfile.reminder1Minute.toLong(),
                        "reminder2Hour" to localProfile.reminder2Hour.toLong(),
                        "reminder2Minute" to localProfile.reminder2Minute.toLong(),
                        "remindersEnabled" to localProfile.remindersEnabled,
                        "themeMode" to com.example.ui.theme.AppThemeMode.fromStorageKey(localProfile.themeMode).storageKey,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "startedAtMillis" to localProfile.startedAtMillis,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    try {
                        profileRef.set(createPayload).await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.CREATE, profileRef.path, auth)
                        throw e
                    }
                }
            } else if (cloudProfileSnap != null && cloudProfile != null && (localProfile == null || (!localProfile.pendingSync && cloudDocHasSetup) || (!localProfile.setupCompleted && cloudDocHasSetup))) {
                val localStarted = localProfile?.startedAtMillis?.takeIf { it > 0L }
                val cloudStarted = cloudStartedMillis.takeIf { it > 0L }
                val resolvedStartedAt = when {
                    localStarted != null && cloudStarted != null -> minOf(localStarted, cloudStarted)
                    else -> localStarted ?: cloudStarted ?: 0L
                }
                val resolvedTarget = (if (rawDailyTarget > 0L) rawDailyTarget else cloudProfile.dailyTarget)
                    .toInt()
                    .coerceIn(1, ONE_CRORE.toInt())
                val resolvedR1Hour = (runCatching { cloudProfileSnap?.getLong("reminder1Hour") }.getOrNull()
                    ?: cloudProfile.reminder1Hour).toInt().coerceIn(0, 23)
                val resolvedR1Minute = (runCatching { cloudProfileSnap?.getLong("reminder1Minute") }.getOrNull()
                    ?: cloudProfile.reminder1Minute).toInt().coerceIn(0, 59)
                val resolvedR2Hour = (runCatching { cloudProfileSnap?.getLong("reminder2Hour") }.getOrNull()
                    ?: cloudProfile.reminder2Hour).toInt().coerceIn(0, 23)
                val resolvedR2Minute = (runCatching { cloudProfileSnap?.getLong("reminder2Minute") }.getOrNull()
                    ?: cloudProfile.reminder2Minute).toInt().coerceIn(0, 59)
                val resolvedRemindersEnabled = runCatching { cloudProfileSnap?.getBoolean("remindersEnabled") }.getOrNull()
                    ?: cloudProfile.remindersEnabled
                val rawCloudTheme = runCatching { cloudProfileSnap?.getString("themeMode") }.getOrNull()
                    ?: cloudProfile.themeMode
                val resolvedThemeMode = if (localProfile != null && localProfile.pendingSync && !localProfile.setupCompleted) {
                    com.example.ui.theme.AppThemeMode.fromStorageKey(localProfile.themeMode).storageKey
                } else {
                    com.example.ui.theme.AppThemeMode.fromStorageKey(rawCloudTheme).storageKey
                }
                profileDao.upsertProfile(
                    UserProfileEntity(
                        userId = uid,
                        displayName = cloudProfile.displayName.ifBlank { localProfile?.displayName ?: "Friend" },
                        dailyTarget = resolvedTarget,
                        setupCompleted = cloudDocHasSetup || (localProfile?.setupCompleted == true),
                        reminder1Hour = resolvedR1Hour,
                        reminder1Minute = resolvedR1Minute,
                        reminder2Hour = resolvedR2Hour,
                        reminder2Minute = resolvedR2Minute,
                        remindersEnabled = resolvedRemindersEnabled,
                        startedAtMillis = resolvedStartedAt,
                        lastSyncedAtMillis = localProfile?.lastSyncedAtMillis ?: 0L,
                        pendingSync = false,
                        updatedAtMillis = System.currentTimeMillis(),
                        themeMode = resolvedThemeMode
                    )
                )
                if (cloudDocHasSetup && !cloudProfile.setupCompleted) {
                    try {
                        profileRef.update(
                            mapOf(
                                "setupCompleted" to true,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            } else if (cloudProfileSnap != null && localProfile != null && localProfile.pendingSync && localProfile.setupCompleted) {
                val cloudStarted = cloudStartedMillis.takeIf { it > 0L }
                if (cloudStarted != null && (localProfile.startedAtMillis <= 0L || cloudStarted < localProfile.startedAtMillis)) {
                    profileDao.upsertProfile(localProfile.copy(startedAtMillis = cloudStarted))
                }
                val updatePayload = mapOf(
                    "startedAtMillis" to (cloudStarted?.takeIf { localProfile.startedAtMillis <= 0L || it < localProfile.startedAtMillis } ?: localProfile.startedAtMillis),
                    "displayName" to localProfile.displayName.ifBlank { "Friend" }.take(80),
                    "dailyTarget" to localProfile.dailyTarget.toLong(),
                    "setupCompleted" to true,
                    "reminder1Hour" to localProfile.reminder1Hour.toLong(),
                    "reminder1Minute" to localProfile.reminder1Minute.toLong(),
                    "reminder2Hour" to localProfile.reminder2Hour.toLong(),
                    "reminder2Minute" to localProfile.reminder2Minute.toLong(),
                    "remindersEnabled" to localProfile.remindersEnabled,
                    "themeMode" to com.example.ui.theme.AppThemeMode.fromStorageKey(localProfile.themeMode).storageKey,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                try {
                    profileRef.update(updatePayload).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, profileRef.path, auth)
                    throw e
                }
            }

            // 2. Fetch cloud daily counts
            val cloudCountsSnap = try {
                countsCollection.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, countsCollection.path, auth)
                throw e
            }

            val cloudByDate = cloudCountsSnap.documents.mapNotNull { doc ->
                doc.toObject(FirestoreDailyCount::class.java)?.let { model ->
                    val key = model.dateKey.ifBlank { doc.id }
                    key to model
                }
            }.toMap()

            // 3. Push explicit manual edits first so intentional reductions stay authoritative
            val pushedManualDateKeys = mutableSetOf<String>()
            val localDaysBeforeMerge = dailyDao.getAllDays(uid)
            for (local in localDaysBeforeMerge.filter { it.isManualEdit && it.pendingSync }) {
                pushDayToCloud(uid, local, cloudByDate.containsKey(local.dateKey), countsCollection.path)
                dailyDao.markDaySynced(uid, local.dateKey)
                pushedManualDateKeys.add(local.dateKey)
            }

            // 4. Merge cloud days using conflict rules:
            //    - Today: device count wins when a local today row exists; if no local today row exists, pull cloud.
            //    - Past days: the larger count wins; cloud NEVER overwrites local with a smaller number.
            val todayDateKey = LocalDate.now().toString()
            val localMapAfterEdits = dailyDao.getAllDays(uid).associateBy { it.dateKey }.toMutableMap()
            for ((dateKey, cloudDay) in cloudByDate) {
                if (dateKey in pushedManualDateKeys) continue
                val local = localMapAfterEdits[dateKey]
        if (local != null && local.isManualEdit && local.pendingSync) {
            pushDayToCloud(uid, local, true, countsCollection.path)
            dailyDao.markDaySynced(uid, local.dateKey)
            continue
        }
                val resolved = resolveDailyCountConflict(
                    uid = uid,
                    dateKey = dateKey,
                    todayDateKey = todayDateKey,
                    local = local,
                    cloudCount = cloudDay.count.coerceAtLeast(0L),
                    cloudUpdatedAtMillis = cloudDay.clientUpdatedAt
                )
                if (resolved != local) {
                    dailyDao.upsertDay(resolved)
                    localMapAfterEdits[dateKey] = resolved
                }
            }

            // 5. Push all remaining pending local days to cloud
            val pendingDays = dailyDao.getPendingDays(uid)
            for (pending in pendingDays) {
                pushDayToCloud(uid, pending, cloudByDate.containsKey(pending.dateKey), countsCollection.path)
                dailyDao.markDaySynced(uid, pending.dateKey)
            }

            // 6. Sync Custom Counters
            val cloudCustomSnap = try {
                customCollection.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, customCollection.path, auth)
                throw e
            }

            val cloudCustomById = cloudCustomSnap.documents.mapNotNull { doc ->
                doc.toObject(FirestoreCustomCounter::class.java)?.let { model ->
                    val id = model.id.ifBlank { doc.id }
                    id to model
                }
            }.toMap()

            // Process deletions & manual edits first
            val pushedManualCustomIds = mutableSetOf<String>()
            val localCustomAll = customDao.getAllCountersIncludingDeleted(uid)
            for (local in localCustomAll) {
                if (local.isDeleted) {
                    if (cloudCustomById.containsKey(local.id)) {
                        val docRef = customCollection.document(local.id)
                        try {
                            docRef.delete().await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
                            throw e
                        }
                    }
                    customDao.hardDeleteCounter(local.id)
                } else if (local.isManualEdit && local.pendingSync) {
                    pushCustomCounterToCloud(uid, local, cloudCustomById.containsKey(local.id), customCollection.path)
                    customDao.markCounterSynced(local.id)
                    pushedManualCustomIds.add(local.id)
                }
            }

            // Merge cloud custom counters (name, targetCount, and currentCount) without overwriting local counts with smaller cloud numbers
            val localCustomMap = customDao.getActiveCounters(uid).associateBy { it.id }.toMutableMap()
            for ((counterId, cloudCounter) in cloudCustomById) {
                if (counterId in pushedManualCustomIds) continue
                val local = localCustomMap[counterId]
                val wasDeletedLocally = localCustomAll.any { it.id == counterId && it.isDeleted }
                if (wasDeletedLocally) continue
                val cloudUpdatedMillis = maxOf(
                    cloudCounter.clientUpdatedAt,
                    cloudCounter.updatedAt?.toDate()?.time ?: 0L
                )
                if (local == null) {
                    val inserted = CustomCounterEntity(
                        id = counterId,
                        userId = uid,
                        name = cloudCounter.name,
                        targetCount = cloudCounter.targetCount.coerceAtLeast(1L),
                        currentCount = cloudCounter.currentCount.coerceAtLeast(0L),
                        pendingSync = false,
                        isManualEdit = false,
                        isDeleted = false,
                        createdAtMillis = cloudUpdatedMillis,
                        updatedAtMillis = cloudUpdatedMillis
                    )
                    customDao.upsertCounter(inserted)
                    localCustomMap[counterId] = inserted
                } else {
                    val cloudName = cloudCounter.name.ifBlank { local.name }
                    val cloudTarget = cloudCounter.targetCount.coerceAtLeast(1L)
                    val cloudCount = cloudCounter.currentCount.coerceAtLeast(0L)

                    val preferCloudMetadata = !local.pendingSync || cloudUpdatedMillis > local.updatedAtMillis
                    val resolvedName = if (preferCloudMetadata) cloudName else local.name
                    val resolvedTarget = if (preferCloudMetadata) cloudTarget else local.targetCount
                    val resolvedCount = maxOf(local.currentCount, cloudCount)
                    val resolvedUpdatedMillis = maxOf(local.updatedAtMillis, cloudUpdatedMillis)

                    val needsUploadToCloud = resolvedCount > cloudCount ||
                        resolvedName != cloudCounter.name ||
                        resolvedTarget != cloudTarget

                    val localFieldsChanged = resolvedName != local.name ||
                        resolvedTarget != local.targetCount ||
                        resolvedCount != local.currentCount

                    if (localFieldsChanged || local.pendingSync != needsUploadToCloud || local.isManualEdit) {
                        val updated = local.copy(
                            name = resolvedName,
                            targetCount = resolvedTarget,
                            currentCount = resolvedCount,
                            pendingSync = needsUploadToCloud,
                            isManualEdit = false,
                            updatedAtMillis = resolvedUpdatedMillis
                        )
                        customDao.upsertCounter(updated)
                        localCustomMap[counterId] = updated
                    }
                }
            }

            val pendingCustom = customDao.getPendingCounters(uid).filter { !it.isDeleted }
            for (pending in pendingCustom) {
                pushCustomCounterToCloud(uid, pending, cloudCustomById.containsKey(pending.id), customCollection.path)
                customDao.markCounterSynced(pending.id)
            }

            // If existing counts or custom counters were found in Firestore (or locally), ensure setupCompleted is true
            val hasAnyCountsOrCounters = cloudByDate.isNotEmpty() ||
                cloudCustomById.isNotEmpty() ||
                dailyDao.getAllDays(uid).isNotEmpty() ||
                customDao.getActiveCounters(uid).isNotEmpty()
            val profileAfterCounts = profileDao.getProfile(uid)
            if (hasAnyCountsOrCounters && (profileAfterCounts == null || !profileAfterCounts.setupCompleted)) {
                val fallbackName = profileAfterCounts?.displayName?.takeIf { it.isNotBlank() }
                    ?: cloudProfile?.displayName?.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: "Friend"
                ensureExistingAccountSetupCompleted(
                    userId = uid,
                    fallbackName = fallbackName,
                    accountCreatedAtMillis = cloudStartedMillis.takeIf { it > 0L }
                        ?: (auth.currentUser?.metadata?.creationTimestamp ?: 0L)
                )
                val healedLocal = profileDao.getProfile(uid)
                if (healedLocal != null) {
                    if (cloudProfileSnap != null && !cloudProfileSnap.exists()) {
                        val createPayload = mapOf(
                            "userId" to uid,
                            "displayName" to healedLocal.displayName.ifBlank { "Friend" }.take(80),
                            "startedAtMillis" to healedLocal.startedAtMillis,
                            "dailyTarget" to healedLocal.dailyTarget.toLong(),
                            "setupCompleted" to true,
                            "reminder1Hour" to healedLocal.reminder1Hour.toLong(),
                            "reminder1Minute" to healedLocal.reminder1Minute.toLong(),
                            "reminder2Hour" to healedLocal.reminder2Hour.toLong(),
                            "reminder2Minute" to healedLocal.reminder2Minute.toLong(),
                            "remindersEnabled" to healedLocal.remindersEnabled,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        try {
                            profileRef.set(createPayload).await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    } else if (cloudProfileSnap != null && (!cloudDocHasSetup || cloudProfile?.setupCompleted != true)) {
                        val updatePayload = mapOf(
                            "displayName" to healedLocal.displayName.ifBlank { "Friend" }.take(80),
                            "startedAtMillis" to healedLocal.startedAtMillis,
                            "dailyTarget" to healedLocal.dailyTarget.toLong(),
                            "setupCompleted" to true,
                            "reminder1Hour" to healedLocal.reminder1Hour.toLong(),
                            "reminder1Minute" to healedLocal.reminder1Minute.toLong(),
                            "reminder2Hour" to healedLocal.reminder2Hour.toLong(),
                            "reminder2Minute" to healedLocal.reminder2Minute.toLong(),
                            "remindersEnabled" to healedLocal.remindersEnabled,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        try {
                            profileRef.update(updatePayload).await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                }
            } else if (profileAfterCounts != null && profileAfterCounts.setupCompleted) {
                val earliestDayMillis = getEarliestLocalDayMillis(uid)
                val earliestStart = listOfNotNull(
                    profileAfterCounts.startedAtMillis.takeIf { it > 0L },
                    cloudStartedMillis.takeIf { it > 0L },
                    earliestDayMillis?.takeIf { it > 0L }
                ).minOrNull()
                if (earliestStart != null && (profileAfterCounts.startedAtMillis <= 0L || earliestStart < profileAfterCounts.startedAtMillis)) {
                    profileDao.upsertProfile(profileAfterCounts.copy(startedAtMillis = earliestStart))
                }
            }

            val syncedAt = System.currentTimeMillis()
            if (profileDao.getProfile(uid) != null) {
                profileDao.markProfileSynced(uid, syncedAt)
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun pushDayToCloud(
        uid: String,
        day: DailyCountEntity,
        existsInCloud: Boolean,
        collectionPath: String
    ) {
        val docRef = firestore.collection("users").document(uid)
            .collection("daily_counts").document(day.dateKey)

        if (existsInCloud) {
            val updatePayload = mapOf(
                "count" to day.count,
                "clientUpdatedAt" to day.updatedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updatePayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "$collectionPath/${day.dateKey}", auth)
                throw e
            }
        } else {
            val createPayload = mapOf(
                "userId" to uid,
                "dateKey" to day.dateKey,
                "count" to day.count,
                "clientUpdatedAt" to day.updatedAtMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(createPayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, "$collectionPath/${day.dateKey}", auth)
                throw e
            }
        }
    }

    private suspend fun pushCustomCounterToCloud(
        uid: String,
        counter: CustomCounterEntity,
        existsInCloud: Boolean,
        collectionPath: String
    ) {
        val docRef = firestore.collection("users").document(uid)
            .collection("custom_counters").document(counter.id)

        if (existsInCloud) {
            val updatePayload = mapOf(
                "name" to counter.name,
                "targetCount" to counter.targetCount,
                "currentCount" to counter.currentCount,
                "clientUpdatedAt" to counter.updatedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updatePayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "$collectionPath/${counter.id}", auth)
                throw e
            }
        } else {
            val createPayload = mapOf(
                "userId" to uid,
                "name" to counter.name,
                "targetCount" to counter.targetCount,
                "currentCount" to counter.currentCount,
                "clientUpdatedAt" to counter.updatedAtMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(createPayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, "$collectionPath/${counter.id}", auth)
                throw e
            }
        }
    }

    suspend fun deleteAllUserData(userId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(uid == userId) { "Authenticated UID mismatch." }

            val userDocRef = firestore.collection("users").document(uid)
            val countsRef = userDocRef.collection("daily_counts")
            val customRef = userDocRef.collection("custom_counters")

        // Mark every local day and custom counter as pending sync first: if the
        // cloud deletion below fails partway, the next sync re-pushes the
        // surviving local rows instead of losing them.
        dailyDao.markAllDaysPendingSync(uid)
        customDao.markAllCountersPendingSync(uid)

            try {
                val snapshot = countsRef.get().await()
                for (doc in snapshot.documents) {
                    doc.reference.delete().await()
                }
                val customSnap = customRef.get().await()
                for (doc in customSnap.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, userDocRef.path, auth)
                throw e
            }

            try {
                userDocRef.delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, userDocRef.path, auth)
                throw e
            }

            dailyDao.deleteAllForUser(uid)
            customDao.deleteAllForUser(uid)
            profileDao.deleteProfile(uid)

            try {
                auth.currentUser?.delete()?.await()
            } catch (_: Exception) {
                auth.signOut()
            return Result.failure(IllegalStateException("Could not delete your account. Please sign in again and retry delete."))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    sealed class GlobalStatsLoadOutcome {
        data class Loaded(val stats: GlobalCounterStats, val fromServer: Boolean) : GlobalStatsLoadOutcome()
        data object ConfirmedMissing : GlobalStatsLoadOutcome()
        data class TransientUnavailable(
            val fallbackCached: GlobalCounterStats?,
            val failureReason: String? = null
        ) : GlobalStatsLoadOutcome()
    }

    suspend fun loadGlobalStats(
        context: Context,
        isOnline: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): GlobalCounterStats? {
        return when (
            val outcome = loadGlobalStatsDetailed(
                context = context,
                isOnline = isOnline,
                forceWhenEmpty = false,
                nowMillis = nowMillis
            )
        ) {
            is GlobalStatsLoadOutcome.Loaded -> outcome.stats
            is GlobalStatsLoadOutcome.ConfirmedMissing -> null
            is GlobalStatsLoadOutcome.TransientUnavailable -> outcome.fallbackCached
        }
    }

    suspend fun loadGlobalStatsDetailed(
        context: Context,
        isOnline: Boolean,
        forceWhenEmpty: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): GlobalStatsLoadOutcome = loadGlobalStatsWithFetcher(
        context = context,
        isOnline = isOnline,
        forceWhenEmpty = forceWhenEmpty,
        nowMillis = nowMillis,
        hasSignedInUser = auth.currentUser != null
    ) { cachedBeforeFetch ->
        val currentUser = auth.currentUser
        if (currentUser == null) {
            return@loadGlobalStatsWithFetcher GlobalStatsLoadOutcome.TransientUnavailable(
                fallbackCached = cachedBeforeFetch,
                failureReason = "AUTH_NULL"
            )
        }

        // Warm up Firebase Auth ID token outside globalStatsMutex so slow IPC never blocks cache ops
        runCatching {
            withTimeoutOrNull(1_500L) {
                currentUser.getIdToken(false).await()
            }
        }

        // Query ONLY the configured named database (R.string.firestore_database_id); never hit non-existent (default) DB.
        fetchGlobalStatsFromFirestore(
            db = firestore,
            dbLabel = "primary",
            cached = cachedBeforeFetch,
            nowMillis = nowMillis
        )
    }

    internal suspend fun loadGlobalStatsWithFetcher(
        context: Context,
        isOnline: Boolean,
        forceWhenEmpty: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
        hasSignedInUser: Boolean = auth.currentUser != null,
        remoteFetcher: suspend (cached: GlobalCounterStats?) -> GlobalStatsLoadOutcome
    ): GlobalStatsLoadOutcome {
        val cachedBeforeFetch = globalStatsMutex.withLock {
            GlobalStatsCache.getCached(context)
        }
        val shouldAttempt = GlobalStatsCache.shouldFetchFromServer(cachedBeforeFetch, nowMillis, isOnline) ||
            (cachedBeforeFetch == null && forceWhenEmpty)
        if (!shouldAttempt) {
            val outcome = if (cachedBeforeFetch != null) {
                GlobalStatsLoadOutcome.Loaded(cachedBeforeFetch, fromServer = false)
            } else {
                GlobalStatsLoadOutcome.TransientUnavailable(null, failureReason = "OFFLINE_SKIPPED")
            }
            Log.i(
                GLOBAL_STATS_LOG_TAG,
                "loadGlobalStatsDetailed skipped network: outcome=${outcome::class.java.simpleName}, online=$isOnline, hasCached=${cachedBeforeFetch != null}"
            )
            return outcome
        }

        if (!hasSignedInUser) {
            val outcome = if (cachedBeforeFetch != null) {
                GlobalStatsLoadOutcome.Loaded(cachedBeforeFetch, fromServer = false)
            } else {
                GlobalStatsLoadOutcome.TransientUnavailable(null, failureReason = "AUTH_NULL")
            }
            Log.i(
                GLOBAL_STATS_LOG_TAG,
                "loadGlobalStatsDetailed skipped (auth.currentUser == null): outcome=${outcome::class.java.simpleName}"
            )
            return outcome
        }

        // Network calls happen strictly OUTSIDE globalStatsMutex so a stalled read cannot wedge later refreshes.
        val remoteOutcome = remoteFetcher(cachedBeforeFetch)

        return when (remoteOutcome) {
            is GlobalStatsLoadOutcome.Loaded -> {
                globalStatsMutex.withLock {
                    GlobalStatsCache.saveCached(context, remoteOutcome.stats)
                }
                Log.i(
                    GLOBAL_STATS_LOG_TAG,
                    "loadGlobalStatsDetailed outcome=Loaded(total=${remoteOutcome.stats.totalCount}, users=${remoteOutcome.stats.userCount}, fromServer=${remoteOutcome.fromServer})"
                )
                remoteOutcome
            }

            is GlobalStatsLoadOutcome.ConfirmedMissing -> {
                globalStatsMutex.withLock {
                    GlobalStatsCache.clearCache(context)
                }
                Log.w(GLOBAL_STATS_LOG_TAG, "loadGlobalStatsDetailed outcome=ConfirmedMissing")
                GlobalStatsLoadOutcome.ConfirmedMissing
            }

            is GlobalStatsLoadOutcome.TransientUnavailable -> {
                val latestCached = globalStatsMutex.withLock {
                    GlobalStatsCache.getCached(context)
                } ?: remoteOutcome.fallbackCached ?: cachedBeforeFetch
                Log.w(
                    GLOBAL_STATS_LOG_TAG,
                    "loadGlobalStatsDetailed outcome=TransientUnavailable(hasFallbackCached=${latestCached != null}, reason=${remoteOutcome.failureReason})"
                )
                GlobalStatsLoadOutcome.TransientUnavailable(
                    fallbackCached = latestCached,
                    failureReason = remoteOutcome.failureReason
                )
            }
        }
    }

    private suspend fun fetchGlobalStatsFromFirestore(
        db: FirebaseFirestore,
        dbLabel: String,
        cached: GlobalCounterStats?,
        nowMillis: Long
    ): GlobalStatsLoadOutcome {
        val docRef = db.collection("stats").document("global")
        return try {
            val snapshot = fetchGlobalDocWithRetry(docRef)
            val rawMap = snapshot.data ?: buildMap<String, Any?> {
                runCatching { snapshot.get("totalCount") }.getOrNull()?.let { put("totalCount", it) }
                runCatching { snapshot.get("userCount") }.getOrNull()?.let { put("userCount", it) }
                runCatching { snapshot.get("sentMilestones") }.getOrNull()?.let { put("sentMilestones", it) }
                runCatching { snapshot.get("milestonesSent") }.getOrNull()?.let { put("milestonesSent", it) }
                runCatching { snapshot.get("milestones") }.getOrNull()?.let { put("milestones", it) }
            }.takeIf { it.isNotEmpty() }

            val parsed = GlobalStatsCache.parseGlobalStatsDocument(
                exists = snapshot.exists(),
                data = rawMap,
                fetchedAtMillis = if (snapshot.metadata.isFromCache) (cached?.fetchedAtMillis ?: nowMillis) else nowMillis
            )
            when {
                parsed != null -> {
                    val fromServer = !snapshot.metadata.isFromCache
                    Log.i(
                        GLOBAL_STATS_LOG_TAG,
                        "fetchGlobalStatsFromFirestore($dbLabel) outcome=Loaded(total=${parsed.totalCount}, users=${parsed.userCount}, fromServer=$fromServer)"
                    )
                    GlobalStatsLoadOutcome.Loaded(parsed, fromServer = fromServer)
                }

                !snapshot.exists() && !snapshot.metadata.isFromCache -> {
                    Log.w(
                        GLOBAL_STATS_LOG_TAG,
                        "fetchGlobalStatsFromFirestore($dbLabel) outcome=ConfirmedMissing(exists=${snapshot.exists()}, fromCache=${snapshot.metadata.isFromCache})"
                    )
                    GlobalStatsLoadOutcome.ConfirmedMissing
                }

                else -> {
                    val unresolvedReason = "UNRESOLVED(exists=${snapshot.exists()},fromCache=${snapshot.metadata.isFromCache},hasData=${rawMap != null})"
                    Log.w(
                        GLOBAL_STATS_LOG_TAG,
                        "fetchGlobalStatsFromFirestore($dbLabel) outcome=TransientUnavailable($unresolvedReason)"
                    )
                    GlobalStatsLoadOutcome.TransientUnavailable(cached, failureReason = unresolvedReason)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val firestoreCode = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name
            val reason = firestoreCode ?: "${e::class.java.simpleName}: ${e.message}"
            Log.w(
                GLOBAL_STATS_LOG_TAG,
                "fetchGlobalStatsFromFirestore($dbLabel) outcome=TransientUnavailable reason=$reason"
            )
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            GlobalStatsLoadOutcome.TransientUnavailable(cached, failureReason = reason)
        }
    }

    private suspend fun fetchGlobalDocWithRetry(
        docRef: com.google.firebase.firestore.DocumentReference,
        timeoutMs: Long = GLOBAL_STATS_FETCH_TIMEOUT_MS
    ): com.google.firebase.firestore.DocumentSnapshot = fetchGlobalWithTimeoutRetry(
        timeoutMs = timeoutMs,
        fetchServer = { docRef.get(Source.SERVER).await() },
        fetchDefault = { docRef.get().await() }
    )

    internal suspend fun <T : Any> fetchGlobalWithTimeoutRetry(
        timeoutMs: Long = GLOBAL_STATS_FETCH_TIMEOUT_MS,
        backoffDelays: LongArray = longArrayOf(350L),
        refreshAuthToken: suspend () -> Unit = {
            runCatching {
                withTimeoutOrNull(1_000L) {
                    auth.currentUser?.getIdToken(false)?.await()
                }
            }
        },
        fetchServer: suspend () -> T,
        fetchDefault: suspend () -> T
    ): T {
        var lastError: Exception? = null
        for (attempt in 0..backoffDelays.size) {
            try {
                val serverResult = withTimeoutOrNull(timeoutMs) {
                    fetchServer()
                }
                if (serverResult != null) {
                    return serverResult
                }
                lastError = TimeoutException(
                    "Timed out reading stats/global from server after ${timeoutMs}ms (attempt ${attempt + 1})"
                )
                Log.w(
                    GLOBAL_STATS_LOG_TAG,
                    "stats/global Source.SERVER timeout after ${timeoutMs}ms (attempt ${attempt + 1})"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.w(
                    GLOBAL_STATS_LOG_TAG,
                    "stats/global Source.SERVER failed on attempt ${attempt + 1}: ${e::class.java.simpleName}: ${e.message}"
                )
            }
            if (attempt < backoffDelays.size) {
                delay(backoffDelays[attempt])
                refreshAuthToken()
            }
        }
        return try {
            val defaultResult = withTimeoutOrNull(timeoutMs) {
                fetchDefault()
            }
            if (defaultResult != null) {
                defaultResult
            } else {
                Log.w(GLOBAL_STATS_LOG_TAG, "stats/global default-source timeout after ${timeoutMs}ms")
                throw lastError ?: TimeoutException("Timed out reading stats/global default source after ${timeoutMs}ms")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(
                GLOBAL_STATS_LOG_TAG,
                "stats/global default-source failed: ${e::class.java.simpleName}: ${e.message}"
            )
            throw lastError ?: e
        }
    }

    fun observeGlobalStatsOnce(
        context: Context,
        onStatsLoaded: (GlobalCounterStats) -> Unit,
        onConfirmedMissing: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ): (() -> Unit)? {
        if (auth.currentUser == null) {
            Log.w(GLOBAL_STATS_LOG_TAG, "observeGlobalStatsOnce skipped: auth.currentUser is null")
            return null
        }
        val docRef = firestore.collection("stats").document("global")
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(
                    GLOBAL_STATS_LOG_TAG,
                    "observeGlobalStatsOnce listener error: ${error::class.java.simpleName}: ${error.message}"
                )
                handleFirestoreError(error, OperationType.GET, docRef.path, auth)
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot == null) {
                val missingSnapshotError = IllegalStateException("stats/global listener received null snapshot")
                Log.w(GLOBAL_STATS_LOG_TAG, "observeGlobalStatsOnce null snapshot")
                onError(missingSnapshotError)
                return@addSnapshotListener
            }
            val nowMillis = System.currentTimeMillis()
            val rawMap = snapshot.data ?: buildMap<String, Any?> {
                runCatching { snapshot.get("totalCount") }.getOrNull()?.let { put("totalCount", it) }
                runCatching { snapshot.get("userCount") }.getOrNull()?.let { put("userCount", it) }
                runCatching { snapshot.get("sentMilestones") }.getOrNull()?.let { put("sentMilestones", it) }
                runCatching { snapshot.get("milestonesSent") }.getOrNull()?.let { put("milestonesSent", it) }
                runCatching { snapshot.get("milestones") }.getOrNull()?.let { put("milestones", it) }
            }.takeIf { it.isNotEmpty() }
            val parsed = GlobalStatsCache.parseGlobalStatsDocument(
                exists = snapshot.exists(),
                data = rawMap,
                fetchedAtMillis = if (snapshot.metadata.isFromCache) (GlobalStatsCache.getCached(context)?.fetchedAtMillis ?: nowMillis) else nowMillis
            )
            if (parsed != null) {
                GlobalStatsCache.saveCached(context, parsed)
                Log.i(
                    GLOBAL_STATS_LOG_TAG,
                    "observeGlobalStatsOnce outcome=Loaded(total=${parsed.totalCount}, users=${parsed.userCount}, fromServer=${!snapshot.metadata.isFromCache})"
                )
                onStatsLoaded(parsed)
            } else if (!snapshot.exists() && !snapshot.metadata.isFromCache) {
                GlobalStatsCache.clearCache(context)
                Log.w(GLOBAL_STATS_LOG_TAG, "observeGlobalStatsOnce outcome=ConfirmedMissing")
                onConfirmedMissing()
            } else {
                Log.d(
                    GLOBAL_STATS_LOG_TAG,
                    "observeGlobalStatsOnce ignored unresolved snapshot (exists=${snapshot.exists()}, fromCache=${snapshot.metadata.isFromCache})"
                )
            }
        }
        Log.i(GLOBAL_STATS_LOG_TAG, "observeGlobalStatsOnce attached listener on ${docRef.path}")
        return { registration.remove() }
    }

    data class AnnouncementFetchResult(
        val items: List<AnnouncementItem>,
        val hadTransientError: Boolean
    )

    suspend fun fetchActiveAnnouncements(
        nowMillis: Long = System.currentTimeMillis()
    ): List<AnnouncementItem> {
        return fetchActiveAnnouncementsDetailed(nowMillis).items
    }

    suspend fun fetchActiveAnnouncementsDetailed(
        nowMillis: Long = System.currentTimeMillis()
    ): AnnouncementFetchResult {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(ANNOUNCEMENT_LOG_TAG, "list skipped: auth.currentUser is null")
            return AnnouncementFetchResult(emptyList(), hadTransientError = true)
        }

        runCatching {
            withTimeoutOrNull(1_500L) {
                currentUser.getIdToken(false).await()
            }
        }

        // Query ONLY the configured named database (R.string.firestore_database_id); never hit non-existent (default) DB.
        return fetchAnnouncementsFromDb(
            db = firestore,
            dbLabel = "primary",
            nowMillis = nowMillis
        )
    }

    private suspend fun fetchAnnouncementsFromDb(
        db: FirebaseFirestore,
        dbLabel: String,
        nowMillis: Long
    ): AnnouncementFetchResult {
        val collectionRef = db.collection("announcements")
        return fetchAnnouncementListWithTimeoutRetry(
            dbLabel = dbLabel,
            nowMillis = nowMillis,
            fetchServer = {
                val snap = collectionRef.get(Source.SERVER).await()
                AnnouncementSnapshotBatch(
                    isFromCache = snap.metadata.isFromCache,
                    docs = snap.documents.map { it.id to it.data }
                )
            },
            fetchDefault = {
                val snap = collectionRef.get().await()
                AnnouncementSnapshotBatch(
                    isFromCache = snap.metadata.isFromCache,
                    docs = snap.documents.map { it.id to it.data }
                )
            },
            onFinalFailure = { err ->
                handleFirestoreError(err, OperationType.LIST, collectionRef.path, auth)
            }
        )
    }

    internal data class AnnouncementSnapshotBatch(
        val isFromCache: Boolean,
        val docs: List<Pair<String, Map<String, Any?>?>>
    )

    internal data class AnnouncementSingleSnapshot(
        val exists: Boolean,
        val isFromCache: Boolean,
        val id: String,
        val data: Map<String, Any?>?
    )

    internal suspend fun fetchAnnouncementListWithTimeoutRetry(
        dbLabel: String = "primary",
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = ANNOUNCEMENT_FETCH_TIMEOUT_MS,
        backoffDelays: LongArray = longArrayOf(350L, 900L),
        refreshAuthToken: suspend () -> Unit = {
            runCatching {
                withTimeoutOrNull(1_000L) {
                    auth.currentUser?.getIdToken(false)?.await()
                }
            }
        },
        fetchServer: suspend () -> AnnouncementSnapshotBatch,
        fetchDefault: suspend () -> AnnouncementSnapshotBatch,
        onFinalFailure: (Exception) -> Unit = {}
    ): AnnouncementFetchResult {
        var lastServerError: Exception? = null

        for (attempt in 0..backoffDelays.size) {
            try {
                val batch = withTimeoutOrNull(timeoutMs) {
                    fetchServer()
                }
                if (batch == null) {
                    lastServerError = TimeoutException(
                        "Timed out fetching active announcements ($dbLabel) after ${timeoutMs}ms (attempt ${attempt + 1})"
                    )
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "list($dbLabel) Timeout after ${timeoutMs}ms (attempt ${attempt + 1})"
                    )
                } else if (batch.isFromCache) {
                    lastServerError = IllegalStateException("Server query returned cached batch ($dbLabel)")
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "list($dbLabel) Rejected cached snapshot from server attempt ${attempt + 1}"
                    )
                } else {
                    val parsed = parseAndSortAnnouncementEntries(batch.docs, nowMillis)
                    Log.i(
                        ANNOUNCEMENT_LOG_TAG,
                        "list($dbLabel) Loaded count=${parsed.size} ids=${parsed.map { it.id }}"
                    )
                    return AnnouncementFetchResult(items = parsed, hadTransientError = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastServerError = e
                Log.w(
                    ANNOUNCEMENT_LOG_TAG,
                    "list($dbLabel) Failed attempt ${attempt + 1}: ${e::class.java.simpleName}: ${e.message}"
                )
            }
            if (attempt < backoffDelays.size) {
                delay(backoffDelays[attempt])
                refreshAuthToken()
            }
        }

        return try {
            val fallbackBatch = withTimeoutOrNull(timeoutMs) {
                fetchDefault()
            }
            when {
                fallbackBatch == null -> {
                    val timeoutErr = TimeoutException(
                        "Timed out reading default announcements ($dbLabel) after ${timeoutMs}ms"
                    )
                    Log.w(ANNOUNCEMENT_LOG_TAG, "list($dbLabel) Failed fallback: Timeout after ${timeoutMs}ms")
                    onFinalFailure(lastServerError ?: timeoutErr)
                    AnnouncementFetchResult(items = emptyList(), hadTransientError = true)
                }
                fallbackBatch.isFromCache -> {
                    // Never surface stale/deactivated cached docs when the live server read failed
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "list($dbLabel) Failed: rejected stale cache fallback (docs=${fallbackBatch.docs.size})"
                    )
                    onFinalFailure(
                        lastServerError ?: IllegalStateException("Announcements live read failed; cache fallback suppressed")
                    )
                    AnnouncementFetchResult(items = emptyList(), hadTransientError = true)
                }
                else -> {
                    val parsed = parseAndSortAnnouncementEntries(fallbackBatch.docs, nowMillis)
                    Log.i(
                        ANNOUNCEMENT_LOG_TAG,
                        "list($dbLabel) Loaded via default server source count=${parsed.size} ids=${parsed.map { it.id }}"
                    )
                    AnnouncementFetchResult(items = parsed, hadTransientError = false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(
                ANNOUNCEMENT_LOG_TAG,
                "list($dbLabel) Failed: ${e::class.java.simpleName}: ${e.message}"
            )
            onFinalFailure(lastServerError ?: e)
            AnnouncementFetchResult(items = emptyList(), hadTransientError = true)
        }
    }

    internal fun parseAndSortAnnouncementEntries(
        entries: List<Pair<String, Map<String, Any?>?>>,
        nowMillis: Long
    ): List<AnnouncementItem> {
        return entries
            .mapNotNull { (docId, data) ->
                AnnouncementParser.parseAnnouncementDocument(
                    id = docId,
                    data = data,
                    allowInactiveOrExpired = false
                )
            }
            .filter { item ->
                nowMillis in item.effectiveStartAtMillis..item.endAtMillis
            }
            .sortedWith(
                compareByDescending<AnnouncementItem> { it.priority }
                    .thenByDescending { it.effectiveStartAtMillis }
                    .thenBy { it.id }
            )
    }

    internal fun parseFutureScheduledAnnouncementEntries(
        entries: List<Pair<String, Map<String, Any?>?>>,
        nowMillis: Long
    ): List<AnnouncementItem> {
        return entries
            .mapNotNull { (docId, data) ->
                AnnouncementParser.parseAnnouncementDocument(
                    id = docId,
                    data = data,
                    allowInactiveOrExpired = false
                )
            }
            .filter { item ->
                item.effectiveStartAtMillis > nowMillis && item.endAtMillis >= item.effectiveStartAtMillis
            }
    }

    suspend fun scheduleOrPublishAnnouncement(
        context: Context,
        item: AnnouncementItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<AnnouncementItem> {
        val appContext = context.applicationContext
        com.example.announcements.AnnouncementScheduledDispatcher.scheduleOrDispatchAnnouncement(
            context = appContext,
            item = item,
            nowMillis = nowMillis
        )
        val cloudWrite = runCatching {
            withTimeoutOrNull(4_000L) {
                firestore.collection("announcements")
                    .document(item.id)
                    .set(item.toFirestoreMap(nowMillis))
                    .await()
            }
        }.onFailure { err ->
            Log.i(
                ANNOUNCEMENT_LOG_TAG,
                "Scheduled announcement ${item.id} queued locally (cloud write deferred to Admin trigger: ${err.message})"
            )
        }
                val writeError = cloudWrite.exceptionOrNull()
        if (writeError != null) return Result.failure(writeError)
        if (cloudWrite.getOrNull() == null) return Result.failure(java.util.concurrent.TimeoutException("Announcement cloud write timed out"))
        return Result.success(item)
    }

    suspend fun fetchAnnouncementById(announcementId: String): AnnouncementItem? {
        val cleanId = announcementId.trim()
        if (cleanId.isEmpty()) {
            Log.w(ANNOUNCEMENT_LOG_TAG, "direct skipped: blank announcementId")
            return null
        }
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(ANNOUNCEMENT_LOG_TAG, "direct(id=$cleanId) skipped: auth.currentUser is null")
            return null
        }

        runCatching {
            withTimeoutOrNull(1_500L) {
                currentUser.getIdToken(false).await()
            }
        }

        // Query ONLY the configured named database (R.string.firestore_database_id); never hit non-existent (default) DB.
        return fetchSingleAnnouncementFromDb(firestore, "primary", cleanId)
    }

    private suspend fun fetchSingleAnnouncementFromDb(
        db: FirebaseFirestore,
        dbLabel: String,
        announcementId: String
    ): AnnouncementItem? {
        val docRef = db.collection("announcements").document(announcementId)
        return fetchAnnouncementByIdWithTimeoutRetry(
            dbLabel = dbLabel,
            announcementId = announcementId,
            fetchServer = {
                val snap = docRef.get(Source.SERVER).await()
                AnnouncementSingleSnapshot(
                    exists = snap.exists(),
                    isFromCache = snap.metadata.isFromCache,
                    id = snap.id,
                    data = snap.data
                )
            },
            fetchDefault = {
                val snap = docRef.get().await()
                AnnouncementSingleSnapshot(
                    exists = snap.exists(),
                    isFromCache = snap.metadata.isFromCache,
                    id = snap.id,
                    data = snap.data
                )
            },
            onFinalFailure = { err ->
                handleFirestoreError(err, OperationType.GET, docRef.path, auth)
            }
        )
    }

    internal suspend fun fetchAnnouncementByIdWithTimeoutRetry(
        dbLabel: String = "primary",
        announcementId: String,
        timeoutMs: Long = ANNOUNCEMENT_FETCH_TIMEOUT_MS,
        backoffDelays: LongArray = longArrayOf(300L, 750L),
        refreshAuthToken: suspend () -> Unit = {
            runCatching {
                withTimeoutOrNull(1_000L) {
                    auth.currentUser?.getIdToken(false)?.await()
                }
            }
        },
        fetchServer: suspend () -> AnnouncementSingleSnapshot,
        fetchDefault: suspend () -> AnnouncementSingleSnapshot,
        onFinalFailure: (Exception) -> Unit = {}
    ): AnnouncementItem? {
        var lastServerError: Exception? = null
        for (attempt in 0..backoffDelays.size) {
            try {
                val snap = withTimeoutOrNull(timeoutMs) {
                    fetchServer()
                }
                if (snap == null) {
                    lastServerError = TimeoutException(
                        "Timed out reading announcements/$announcementId ($dbLabel) after ${timeoutMs}ms (attempt ${attempt + 1})"
                    )
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "direct($dbLabel, id=$announcementId) Timeout after ${timeoutMs}ms (attempt ${attempt + 1})"
                    )
                } else if (snap.isFromCache) {
                    lastServerError = IllegalStateException(
                        "Server read returned cached snapshot for announcements/$announcementId ($dbLabel)"
                    )
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "direct($dbLabel, id=$announcementId) Rejected cached snapshot on server attempt ${attempt + 1}"
                    )
                } else if (snap.exists) {
                    val parsed = AnnouncementParser.parseAnnouncementDocument(
                        id = snap.id,
                        data = snap.data,
                        allowInactiveOrExpired = true
                    )
                    if (parsed != null) {
                        Log.i(
                            ANNOUNCEMENT_LOG_TAG,
                            "direct($dbLabel, id=$announcementId) Loaded(hasButton=${parsed.hasActionButton})"
                        )
                        return parsed
                    }
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "direct($dbLabel, id=$announcementId) Failed: live document unparseable"
                    )
                    return null
                } else {
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "direct($dbLabel, id=$announcementId) Missing on server"
                    )
                    return null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastServerError = e
                Log.w(
                    ANNOUNCEMENT_LOG_TAG,
                    "direct($dbLabel, id=$announcementId) Failed attempt ${attempt + 1}: ${e::class.java.simpleName}: ${e.message}"
                )
            }
            if (attempt < backoffDelays.size) {
                delay(backoffDelays[attempt])
                refreshAuthToken()
            }
        }

        return try {
            val fallbackSnap = withTimeoutOrNull(timeoutMs) {
                fetchDefault()
            }
            when {
                fallbackSnap == null -> {
                    val timeoutErr = TimeoutException(
                        "Timed out default read for announcements/$announcementId ($dbLabel) after ${timeoutMs}ms"
                    )
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "direct($dbLabel, id=$announcementId) Failed fallback: Timeout after ${timeoutMs}ms"
                    )
                    onFinalFailure(lastServerError ?: timeoutErr)
                    null
                }
                fallbackSnap.isFromCache -> {
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "direct($dbLabel, id=$announcementId) Failed: rejected stale cache fallback"
                    )
                    onFinalFailure(
                        lastServerError ?: IllegalStateException("Direct announcement live read failed; cache suppressed")
                    )
                    null
                }
                fallbackSnap.exists -> {
                    val parsed = AnnouncementParser.parseAnnouncementDocument(
                        id = fallbackSnap.id,
                        data = fallbackSnap.data,
                        allowInactiveOrExpired = true
                    )
                    if (parsed != null) {
                        Log.i(
                            ANNOUNCEMENT_LOG_TAG,
                            "direct($dbLabel, id=$announcementId) Loaded via default server source(hasButton=${parsed.hasActionButton})"
                        )
                    } else {
                        Log.w(
                            ANNOUNCEMENT_LOG_TAG,
                            "direct($dbLabel, id=$announcementId) Failed: default server doc unparseable"
                        )
                    }
                    parsed
                }
                else -> {
                    Log.w(
                        ANNOUNCEMENT_LOG_TAG,
                        "direct($dbLabel, id=$announcementId) Missing on default server source"
                    )
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(
                ANNOUNCEMENT_LOG_TAG,
                "direct($dbLabel, id=$announcementId) Failed: ${e::class.java.simpleName}: ${e.message}"
            )
            onFinalFailure(lastServerError ?: e)
            null
        }
    }

    companion object {
        private val syncMutex = Mutex()
        private val globalStatsMutex = Mutex()
    
        const val ONE_CRORE = 10_000_000L
        const val DEFAULT_DAILY_TARGET = 2600
        const val GLOBAL_STATS_FETCH_TIMEOUT_MS = 9_000L
        const val ANNOUNCEMENT_FETCH_TIMEOUT_MS = GLOBAL_STATS_FETCH_TIMEOUT_MS
        const val GLOBAL_STATS_LOG_TAG = "GlobalStats"
        const val ANNOUNCEMENT_LOG_TAG = "Announcements"

        /**
         * Pure conflict resolution rule for daily counts:
         * - If no local record exists (`local == null`), pull the cloud count (`pendingSync = false`).
         * - For TODAY (`dateKey == todayDateKey`): when a local record exists on the device,
         *   the device count wins (and cloud NEVER overwrites local with a smaller number).
         *   If `local.count != cloudCount`, `pendingSync = true` so today's device count uploads to Firestore.
         * - For PAST DAYS (`dateKey != todayDateKey`): the larger count (`maxOf(local.count, cloudCount)`) wins;
         *   cloud NEVER overwrites local with a smaller number.
         */
        fun resolveDailyCountConflict(
            uid: String,
            dateKey: String,
            todayDateKey: String,
            local: DailyCountEntity?,
            cloudCount: Long,
            cloudUpdatedAtMillis: Long
        ): DailyCountEntity {
            val safeCloudCount = cloudCount.coerceAtLeast(0L)
            if (local == null) {
                return DailyCountEntity(
                    userId = uid,
                    dateKey = dateKey,
                    count = safeCloudCount,
                    pendingSync = false,
                    isManualEdit = false,
                    updatedAtMillis = cloudUpdatedAtMillis
                )
            }
            val isToday = dateKey == todayDateKey
            return if (isToday) {
                // Today -> same rule as past days: the larger count wins; cloud NEVER overwrites local with a smaller number
                val resolved = maxOf(local.count, safeCloudCount)
                local.copy(
                    count = resolved,
                    pendingSync = local.count > safeCloudCount,
                    isManualEdit = false,
                    updatedAtMillis = if (safeCloudCount > local.count) maxOf(local.updatedAtMillis, cloudUpdatedAtMillis) else local.updatedAtMillis
                )
            } else {
                // Past days -> the larger count wins; cloud NEVER overwrites local with a smaller number
                when {
                    safeCloudCount > local.count -> local.copy(
                        count = safeCloudCount,
                        pendingSync = false,
                        isManualEdit = false,
                        updatedAtMillis = maxOf(local.updatedAtMillis, cloudUpdatedAtMillis)
                    )
                    local.count > safeCloudCount -> local.copy(
                        count = local.count,
                        pendingSync = true
                    )
                    else -> local.copy(
                        pendingSync = false,
                        isManualEdit = false
                    )
                }
            }
        }
    }
}
