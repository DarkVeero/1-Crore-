package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): UserProfileEntity?

    @Query("SELECT userId FROM user_profiles ORDER BY updatedAtMillis DESC LIMIT 1")
    suspend fun getLatestUserId(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET lastSyncedAtMillis = :syncedAt, pendingSync = 0 WHERE userId = :userId")
    suspend fun markProfileSynced(userId: String, syncedAt: Long)

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteProfile(userId: String)
}

@Dao
interface DailyCountDao {
    @Query("SELECT * FROM daily_counts WHERE userId = :userId ORDER BY dateKey DESC")
    fun observeAllDays(userId: String): Flow<List<DailyCountEntity>>

    @Query("SELECT * FROM daily_counts WHERE userId = :userId ORDER BY dateKey DESC LIMIT :limit")
    fun observeRecentDays(userId: String, limit: Int): Flow<List<DailyCountEntity>>

    @Query("SELECT COALESCE(SUM(count), 0) FROM daily_counts WHERE userId = :userId")
    fun observeTotalCount(userId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(count), 0) FROM daily_counts WHERE userId = :userId")
    suspend fun getTotalCountOnce(userId: String): Long

    @Query("SELECT COALESCE(MAX(count), 0) FROM daily_counts WHERE userId = :userId AND dateKey = :dateKey")
    fun observeDayCount(userId: String, dateKey: String): Flow<Long>

    @Query(
        "SELECT (" +
            "EXISTS(SELECT 1 FROM daily_counts WHERE userId = :userId AND pendingSync = 1) OR " +
            "EXISTS(SELECT 1 FROM user_profiles WHERE userId = :userId AND pendingSync = 1) OR " +
            "EXISTS(SELECT 1 FROM custom_counters WHERE userId = :userId AND pendingSync = 1)" +
        ")"
    )
    fun observeHasPendingSync(userId: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM daily_counts WHERE userId = :userId AND pendingSync = 1")
    fun observePendingDaysCount(userId: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(count), 0) FROM daily_counts WHERE userId = :userId AND pendingSync = 1")
    fun observePendingDuroodTotal(userId: String): Flow<Long>

    @Query("SELECT * FROM daily_counts WHERE userId = :userId AND dateKey = :dateKey LIMIT 1")
    suspend fun getDay(userId: String, dateKey: String): DailyCountEntity?

    @Query("SELECT * FROM daily_counts WHERE userId = :userId ORDER BY dateKey DESC")
    suspend fun getAllDays(userId: String): List<DailyCountEntity>

    @Query("SELECT * FROM daily_counts WHERE userId = :userId AND pendingSync = 1 ORDER BY dateKey ASC")
    suspend fun getPendingDays(userId: String): List<DailyCountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(entity: DailyCountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDays(entities: List<DailyCountEntity>)

    @Transaction
    suspend fun incrementDay(userId: String, dateKey: String, delta: Long, nowMillis: Long) {
        if (delta <= 0L) return
        val existing = getDay(userId, dateKey)
        val newCount = (existing?.count ?: 0L) + delta
        upsertDay(
            DailyCountEntity(
                userId = userId,
                dateKey = dateKey,
                count = newCount,
                pendingSync = true,
                isManualEdit = existing?.isManualEdit ?: false,
                updatedAtMillis = nowMillis
            )
        )
    }

    @Query("UPDATE daily_counts SET pendingSync = 0, isManualEdit = 0 WHERE userId = :userId AND dateKey = :dateKey")
    suspend fun markDaySynced(userId: String, dateKey: String)

    @Query("UPDATE daily_counts SET pendingSync = 1 WHERE userId = :userId")
    suspend fun markAllDaysPendingSync(userId: String)

    @Query("DELETE FROM daily_counts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface CustomCounterDao {
    @Query("SELECT * FROM custom_counters WHERE userId = :userId AND isDeleted = 0 ORDER BY createdAtMillis DESC")
    fun observeActiveCounters(userId: String): Flow<List<CustomCounterEntity>>

    @Query("SELECT * FROM custom_counters WHERE id = :id AND isDeleted = 0 LIMIT 1")
    fun observeCounter(id: String): Flow<CustomCounterEntity?>

    @Query("SELECT * FROM custom_counters WHERE id = :id LIMIT 1")
    suspend fun getCounter(id: String): CustomCounterEntity?

    @Query("SELECT * FROM custom_counters WHERE userId = :userId")
    suspend fun getAllCountersIncludingDeleted(userId: String): List<CustomCounterEntity>

    @Query("SELECT * FROM custom_counters WHERE userId = :userId AND isDeleted = 0")
    suspend fun getActiveCounters(userId: String): List<CustomCounterEntity>

    @Query("SELECT * FROM custom_counters WHERE userId = :userId AND pendingSync = 1")
    suspend fun getPendingCounters(userId: String): List<CustomCounterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCounter(entity: CustomCounterEntity)

    @Transaction
    suspend fun incrementCounter(userId: String, counterId: String, delta: Long, nowMillis: Long) {
        if (delta <= 0L) return
        val existing = getCounter(counterId) ?: return
        if (existing.userId != userId || existing.isDeleted) return
        upsertCounter(
            existing.copy(
                currentCount = existing.currentCount + delta,
                pendingSync = true,
                updatedAtMillis = nowMillis
            )
        )
    }

    @Query("UPDATE custom_counters SET pendingSync = 0, isManualEdit = 0 WHERE id = :id")
    suspend fun markCounterSynced(id: String)

    @Query("DELETE FROM custom_counters WHERE id = :id")
    suspend fun hardDeleteCounter(id: String)

    @Query("UPDATE custom_counters SET pendingSync = 1 WHERE userId = :userId")
    suspend fun markAllCountersPendingSync(userId: String)

    @Query("DELETE FROM custom_counters WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
