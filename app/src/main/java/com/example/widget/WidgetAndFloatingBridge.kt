package com.example.widget

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.sync.NetworkMonitor
import com.example.sync.SyncWorker
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WidgetCountsSnapshot(
    val userId: String,
    val todayCount: Long,
    val totalCount: Long
)

/**
 * Shared persistence bridge ensuring the Home-Screen Widget, the Floating Counter Button,
 * and the main app all read and write to the exact same local Room database + auto-sync.
 */
object WidgetAndFloatingBridge {
    const val BATCH_SYNC_TAP_THRESHOLD = 100L
    // R3-P2-27: the fallback id for taps made before any sign-in. Counts
    // written under it were orphaned on sign-in (nothing migrates them), so
    // the write paths refuse to count under it — see incrementCountAndSync.
    const val FALLBACK_USER_ID = "local_default_user"
    private const val PREFS_NAME = "one_crore_widget_floating_prefs"
    private const val KEY_ACTIVE_USER_ID = "active_user_id"
    private const val KEY_CACHED_TODAY_COUNT = "cached_today_count"
    private const val KEY_CACHED_TOTAL_COUNT = "cached_total_count"
    private const val KEY_CACHED_DATE_KEY = "cached_date_key"
    private const val KEY_UNSYNCED_TAP_COUNT = "unsynced_tap_count"

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun setActiveUserId(context: Context, userId: String?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (userId.isNullOrBlank()) {
            prefs.edit().remove(KEY_ACTIVE_USER_ID).apply()
        } else {
            prefs.edit().putString(KEY_ACTIVE_USER_ID, userId.trim()).apply()
        }
    }

    fun getStoredActiveUserId(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_USER_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun getUnsyncedTapCount(context: Context): Long {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_UNSYNCED_TAP_COUNT, 0L).coerceAtLeast(0L)
    }

    fun resetUnsyncedTapCount(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_UNSYNCED_TAP_COUNT, 0L)
            .apply()
    }

    fun cacheLatestCounts(
        context: Context,
        todayCount: Long,
        totalCount: Long,
        dateKey: String = LocalDate.now().toString()
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_CACHED_TODAY_COUNT, todayCount.coerceAtLeast(0L))
            .putLong(KEY_CACHED_TOTAL_COUNT, totalCount.coerceAtLeast(0L))
            .putString(KEY_CACHED_DATE_KEY, dateKey)
            .apply()
    }

    fun getCachedCounts(
        context: Context,
        todayKey: String = LocalDate.now().toString()
    ): Pair<Long, Long> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedDate = prefs.getString(KEY_CACHED_DATE_KEY, null)
        val today = if (storedDate == todayKey) {
            prefs.getLong(KEY_CACHED_TODAY_COUNT, 0L).coerceAtLeast(0L)
        } else {
            0L
        }
        val total = prefs.getLong(KEY_CACHED_TOTAL_COUNT, 0L).coerceAtLeast(0L)
        return today to total
    }

    suspend fun resolveUserId(
        context: Context,
        db: AppDatabase = AppDatabase.getInstance(context),
        userIdOverride: String? = null
    ): String {
        if (!userIdOverride.isNullOrBlank()) return userIdOverride.trim()
        getStoredActiveUserId(context)?.let { return it }
        val firebaseUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!firebaseUid.isNullOrBlank()) {
            setActiveUserId(context, firebaseUid)
            return firebaseUid
        }
        val latestFromDb = runCatching { db.userProfileDao().getLatestUserId() }.getOrNull()
        if (!latestFromDb.isNullOrBlank()) {
            setActiveUserId(context, latestFromDb)
            return latestFromDb
        }
        return FALLBACK_USER_ID
    }

    /** Synchronous check for a real signed-in/stored user (no DB read). */
    fun hasRealUserId(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!getStoredActiveUserId(appContext).isNullOrBlank()) return true
        return runCatching { Firebase.auth.currentUser?.uid }.getOrNull()?.isNotBlank() == true
    }

    suspend fun readCountsFromDatabase(
        context: Context,
        db: AppDatabase = AppDatabase.getInstance(context),
        userIdOverride: String? = null,
        todayKey: String = LocalDate.now().toString()
    ): WidgetCountsSnapshot {
        val userId = resolveUserId(context, db, userIdOverride)
        val dailyDao = db.dailyCountDao()
        val todayCount = dailyDao.getDay(userId, todayKey)?.count ?: 0L
        val totalCount = dailyDao.getTotalCountOnce(userId)
        cacheLatestCounts(context, todayCount, totalCount, todayKey)
        return WidgetCountsSnapshot(
            userId = userId,
            todayCount = todayCount,
            totalCount = totalCount
        )
    }

    suspend fun incrementCountAndSync(
        context: Context,
        delta: Long = 1L,
        db: AppDatabase = AppDatabase.getInstance(context),
        userIdOverride: String? = null,
        todayKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis()
    ): WidgetCountsSnapshot {
        val appContext = context.applicationContext
        val userId = resolveUserId(appContext, db, userIdOverride)
        // R3-P2-27: never count under the fallback id — those taps were
        // orphaned on sign-in. Refuse the write (and the sync queue) and
        // return an empty snapshot; the user must sign in first.
        if (userId == FALLBACK_USER_ID) {
            return WidgetCountsSnapshot(userId = userId, todayCount = 0L, totalCount = 0L)
        }
        val dailyDao = db.dailyCountDao()
        if (delta > 0L) {
            dailyDao.incrementDay(
                userId = userId,
                dateKey = todayKey,
                delta = delta,
                nowMillis = nowMillis
            )
        }
        val todayCount = dailyDao.getDay(userId, todayKey)?.count ?: 0L
        val totalCount = dailyDao.getTotalCountOnce(userId)
        cacheLatestCounts(appContext, todayCount, totalCount, todayKey)
        OneCroreWidgetProvider.updateAllWidgetsWithCounts(appContext, todayCount, totalCount)

        // Queue background WorkManager outbox sync (NO cloud write per single tap)
        SyncWorker.enqueueWhenOnline(appContext)

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newUnsyncedTaps = prefs.getLong(KEY_UNSYNCED_TAP_COUNT, 0L) + 1L
        if (newUnsyncedTaps >= BATCH_SYNC_TAP_THRESHOLD) {
            prefs.edit().putLong(KEY_UNSYNCED_TAP_COUNT, 0L).apply()
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                bridgeScope.launch {
                    runCatching {
                        com.example.data.DuroodRepository(appContext).syncWithCloud()
                    }
                }
            }
        } else {
            prefs.edit().putLong(KEY_UNSYNCED_TAP_COUNT, newUnsyncedTaps).apply()
        }

        return WidgetCountsSnapshot(
            userId = userId,
            todayCount = todayCount,
            totalCount = totalCount
        )
    }

    fun refreshWidgetsFromDatabaseAsync(
        context: Context,
        todayCountHint: Long? = null,
        totalCountHint: Long? = null
    ) {
        val appContext = context.applicationContext
        if (todayCountHint != null && totalCountHint != null) {
            cacheLatestCounts(appContext, todayCountHint, totalCountHint)
            OneCroreWidgetProvider.updateAllWidgetsWithCounts(appContext, todayCountHint, totalCountHint)
            return
        }
        bridgeScope.launch {
            val snap = readCountsFromDatabase(appContext)
            OneCroreWidgetProvider.updateAllWidgetsWithCounts(
                context = appContext,
                todayCount = snap.todayCount,
                totalCount = snap.totalCount
            )
        }
    }

    fun resetForTest(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
