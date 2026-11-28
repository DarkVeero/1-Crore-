package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.widget.WidgetAndFloatingBridge
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

val Context.reminderPrefs by preferencesDataStore(name = "reminder_prefs")

data class SavedReminderSchedule(
    val userId: String?,
    val enabled: Boolean,
    val r1Hour: Int,
    val r1Minute: Int,
    val r2Hour: Int,
    val r2Minute: Int
)

object ReminderScheduler {
    const val CHANNEL_ID = "one_crore_daily_reminders"
    const val EXTRA_SLOT = "extra_slot"
    private const val REQUEST_CODE_SLOT_1 = 1001
    private const val REQUEST_CODE_SLOT_2 = 1002
    private const val MAX_NOTIFICATIONS_PER_DAY = 2

    private const val SCHEDULE_PREFS_NAME = "one_crore_reminder_schedule_prefs"
    private const val KEY_CFG_USER_ID = "cfg_user_id"
    private const val KEY_CFG_ENABLED = "cfg_enabled"
    private const val KEY_CFG_R1_HOUR = "cfg_r1_hour"
    private const val KEY_CFG_R1_MIN = "cfg_r1_min"
    private const val KEY_CFG_R2_HOUR = "cfg_r2_hour"
    private const val KEY_CFG_R2_MIN = "cfg_r2_min"
    private const val KEY_HAS_CFG = "has_cfg"
    private const val KEY_DUE_SLOT_1_MILLIS = "due_slot_1_millis"
    private const val KEY_DUE_SLOT_2_MILLIS = "due_slot_2_millis"

    private const val WORK_SLOT_1 = "one_crore_reminder_slot_1"
    private const val WORK_SLOT_2 = "one_crore_reminder_slot_2"
    private const val WORK_PERIODIC_SAFEGUARD = "one_crore_reminder_periodic_safeguard"

    private val KEY_SENT_DATE = stringPreferencesKey("reminder_sent_date")
    private val KEY_SENT_COUNT = intPreferencesKey("reminder_sent_count")
    private val KEY_LAST_SENT_SLOT_1_DATE = stringPreferencesKey("reminder_slot_1_sent_date")
    private val KEY_LAST_SENT_SLOT_2_DATE = stringPreferencesKey("reminder_slot_2_sent_date")

    private val evalMutex = Mutex()

    fun isTargetIncomplete(todayCount: Long, dailyTarget: Long): Boolean {
        return todayCount < dailyTarget.coerceAtLeast(1L)
    }

    fun computeNextTrigger(
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
        minAdvanceSeconds: Long = 0L
    ): ZonedDateTime {
        val threshold = now.plusSeconds(minAdvanceSeconds.coerceAtLeast(0L))
        var trigger = now.withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!trigger.isAfter(threshold)) {
            trigger = trigger.plusDays(1)
        }
        return trigger
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.reminder_channel_desc)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun areNotificationsAllowed(context: Context): Boolean {
        val nmCompat = NotificationManagerCompat.from(context)
        if (!nmCompat.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sysManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = sysManager?.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    fun getSavedScheduleConfig(context: Context): SavedReminderSchedule? {
        val prefs = context.applicationContext.getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_CFG, false)) return null
        return SavedReminderSchedule(
            userId = prefs.getString(KEY_CFG_USER_ID, null)?.takeIf { it.isNotBlank() },
            enabled = prefs.getBoolean(KEY_CFG_ENABLED, true),
            r1Hour = prefs.getInt(KEY_CFG_R1_HOUR, 18).coerceIn(0, 23),
            r1Minute = prefs.getInt(KEY_CFG_R1_MIN, 0).coerceIn(0, 59),
            r2Hour = prefs.getInt(KEY_CFG_R2_HOUR, 21).coerceIn(0, 23),
            r2Minute = prefs.getInt(KEY_CFG_R2_MIN, 0).coerceIn(0, 59)
        )
    }

    private fun saveScheduleConfig(
        context: Context,
        userId: String?,
        enabled: Boolean,
        r1Hour: Int,
        r1Minute: Int,
        r2Hour: Int,
        r2Minute: Int
    ) {
        val existingUid = getSavedScheduleConfig(context)?.userId
        val resolvedUid = userId?.takeIf { it.isNotBlank() } ?: existingUid
        context.applicationContext
            .getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_CFG, true)
            .putString(KEY_CFG_USER_ID, resolvedUid)
            .putBoolean(KEY_CFG_ENABLED, enabled)
            .putInt(KEY_CFG_R1_HOUR, r1Hour.coerceIn(0, 23))
            .putInt(KEY_CFG_R1_MIN, r1Minute.coerceIn(0, 59))
            .putInt(KEY_CFG_R2_HOUR, r2Hour.coerceIn(0, 23))
            .putInt(KEY_CFG_R2_MIN, r2Minute.coerceIn(0, 59))
            .apply()
    }

    suspend fun scheduleReminders(
        context: Context,
        enabled: Boolean,
        r1Hour: Int,
        r1Minute: Int,
        r2Hour: Int,
        r2Minute: Int,
        userId: String? = null,
        minAdvanceSecondsSlot1: Long = 0L,
        minAdvanceSecondsSlot2: Long = 0L,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ) {
        val appContext = context.applicationContext
        saveScheduleConfig(
            context = appContext,
            userId = userId,
            enabled = enabled,
            r1Hour = r1Hour,
            r1Minute = r1Minute,
            r2Hour = r2Hour,
            r2Minute = r2Minute
        )

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (!enabled) {
            cancelAll(appContext, alarmManager)
            appContext.getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_DUE_SLOT_1_MILLIS)
                .remove(KEY_DUE_SLOT_2_MILLIS)
                .apply()
            return
        }

        if (alarmManager != null) {
            cancelAlarmsOnly(appContext, alarmManager)
        }

        val trigger1Millis = scheduleSlot(
            context = appContext,
            alarmManager = alarmManager,
            slot = 1,
            hour = r1Hour,
            minute = r1Minute,
            requestCode = REQUEST_CODE_SLOT_1,
            minAdvanceSeconds = minAdvanceSecondsSlot1,
            now = now
        )
        val trigger2Millis = scheduleSlot(
            context = appContext,
            alarmManager = alarmManager,
            slot = 2,
            hour = r2Hour,
            minute = r2Minute,
            requestCode = REQUEST_CODE_SLOT_2,
            minAdvanceSeconds = minAdvanceSecondsSlot2,
            now = now
        )

        // Store the scheduled trigger times (preserve an earlier today trigger only if it hasn't fired yet)
        val schedPrefs = appContext.getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
        val nowMillis = now.toInstant().toEpochMilli()
        val prevDue1 = schedPrefs.getLong(KEY_DUE_SLOT_1_MILLIS, 0L)
        val prevDue2 = schedPrefs.getLong(KEY_DUE_SLOT_2_MILLIS, 0L)
        val keepPrev1 = prevDue1 in 1L..nowMillis && isSameLocalDate(prevDue1, now)
        val keepPrev2 = prevDue2 in 1L..nowMillis && isSameLocalDate(prevDue2, now)

        schedPrefs.edit()
            .putLong(KEY_DUE_SLOT_1_MILLIS, if (keepPrev1 && minAdvanceSecondsSlot1 == 0L) prevDue1 else trigger1Millis)
            .putLong(KEY_DUE_SLOT_2_MILLIS, if (keepPrev2 && minAdvanceSecondsSlot2 == 0L) prevDue2 else trigger2Millis)
            .apply()

        scheduleWorkManagerBackup(appContext, slot = 1, triggerMillis = trigger1Millis, nowMillis = nowMillis)
        scheduleWorkManagerBackup(appContext, slot = 2, triggerMillis = trigger2Millis, nowMillis = nowMillis)
        ensurePeriodicSafeguardWork(appContext)
    }

    private fun isSameLocalDate(epochMillis: Long, reference: ZonedDateTime): Boolean {
        val date = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(reference.zone)
            .toLocalDate()
        return date == reference.toLocalDate()
    }

    @SuppressLint("MissingPermission")
    private fun scheduleSlot(
        context: Context,
        alarmManager: AlarmManager?,
        slot: Int,
        hour: Int,
        minute: Int,
        requestCode: Int,
        minAdvanceSeconds: Long,
        now: ZonedDateTime
    ): Long {
        val trigger = computeNextTrigger(
            now = now,
            hour = hour,
            minute = minute,
            minAdvanceSeconds = minAdvanceSeconds
        )
        val triggerMillis = trigger.toInstant().toEpochMilli()

        if (alarmManager != null) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_SLOT, slot)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } catch (_: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        }
        return triggerMillis
    }

    private fun scheduleWorkManagerBackup(
        context: Context,
        slot: Int,
        triggerMillis: Long,
        nowMillis: Long
    ) {
        try {
            val delayMillis = (triggerMillis - nowMillis).coerceAtLeast(1_000L)
            val workName = if (slot == 1) WORK_SLOT_1 else WORK_SLOT_2
            val request = OneTimeWorkRequestBuilder<ReminderCheckWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(EXTRA_SLOT to slot))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        } catch (_: Exception) {
            // Safe no-op in tests where WorkManager is not initialized
        }
    }

    private fun ensurePeriodicSafeguardWork(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(
                30,
                TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_PERIODIC_SAFEGUARD,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (_: Exception) {
        }
    }

    private fun cancelAlarmsOnly(context: Context, alarmManager: AlarmManager) {
        listOf(1 to REQUEST_CODE_SLOT_1, 2 to REQUEST_CODE_SLOT_2).forEach { (slot, code) ->
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_SLOT, slot)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
            }
        }
    }

    // Round 1 S-P2-6 (SM-D1): was a plain fun that runBlocking-blocked the
    // calling thread (the main thread on sign-out / delete-account) to clear
    // DataStore. Now suspend — every call site already runs in a coroutine.
    suspend fun cancelAll(context: Context, alarmManager: AlarmManager? = null) {
        // Wipe the saved schedule config and the reminder quota (last-sent) state so
        // reminders cannot resurrect from stale persisted state after logout / delete account.
        try {
            context.applicationContext
                .getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        } catch (_: Exception) {
        }
        try {
            context.applicationContext.reminderPrefs.edit { it.clear() }
        } catch (_: Exception) {
        }
        val am = alarmManager ?: (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
        if (am != null) {
            cancelAlarmsOnly(context, am)
        }
        try {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(WORK_SLOT_1)
            wm.cancelUniqueWork(WORK_SLOT_2)
            wm.cancelUniqueWork(WORK_PERIODIC_SAFEGUARD)
        } catch (_: Exception) {
        }
    }

    suspend fun rescheduleFromSavedStateIfPossible(
        context: Context,
        dbOverride: AppDatabase? = null
    ) {
        val appContext = context.applicationContext
        val db = dbOverride ?: AppDatabase.getInstance(appContext)
        val uid = resolveReminderUserId(appContext, db)
        val profile = uid?.let { db.userProfileDao().getProfile(it) }

        if (Firebase.auth.currentUser != null && profile != null && profile.setupCompleted) {
            scheduleReminders(
                context = appContext,
                enabled = profile.remindersEnabled,
                r1Hour = profile.reminder1Hour,
                r1Minute = profile.reminder1Minute,
                r2Hour = profile.reminder2Hour,
                r2Minute = profile.reminder2Minute,
                userId = profile.userId
            )
        }
    }

    private suspend fun resolveReminderUserId(context: Context, db: AppDatabase): String? {
        runCatching { Firebase.auth.currentUser?.uid }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        getSavedScheduleConfig(context)?.userId?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        WidgetAndFloatingBridge.getStoredActiveUserId(context)?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        return db.userProfileDao().getLatestUserId()
    }

    suspend fun evaluateAndSendReminder(
        context: Context,
        slotOverride: Int?,
        allowCatchUp: Boolean = true,
        dbOverride: AppDatabase? = null,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): Boolean = evalMutex.withLock {
        val appContext = context.applicationContext
        val db = dbOverride ?: AppDatabase.getInstance(appContext)
        val uid = resolveReminderUserId(appContext, db) ?: return@withLock false
        // R3-P2-39: a surviving alarm/WorkManager run must not remind a
        // logged-out or deleted user. cancelAll is the only defence today,
        // and it can fail part-way (or a run can already be in flight), so
        // the reminder itself checks the signed-in account matches the
        // resolved uid before doing anything.
        if (Firebase.auth.currentUser?.uid != uid) return@withLock false
        val profile = db.userProfileDao().getProfile(uid)
        val savedCfg = getSavedScheduleConfig(appContext)

        val enabled = profile?.remindersEnabled ?: savedCfg?.enabled ?: false
        val setupCompleted = (profile?.setupCompleted == true) || (savedCfg != null)
        val r1H = profile?.reminder1Hour ?: savedCfg?.r1Hour ?: 18
        val r1M = profile?.reminder1Minute ?: savedCfg?.r1Minute ?: 0
        val r2H = profile?.reminder2Hour ?: savedCfg?.r2Hour ?: 21
        val r2M = profile?.reminder2Minute ?: savedCfg?.r2Minute ?: 0
        val dailyTarget = (profile?.dailyTarget ?: 2600).coerceAtLeast(1).toLong()

        val activeSlot = slotOverride ?: findDueCatchUpSlot(appContext, now)

        // Reschedule upcoming reminders; advance past today's window for the slot that just fired
        // so minor RTC wakeups (e.g. 17:59:59) don't re-trigger the same slot a second later.
        scheduleReminders(
            context = appContext,
            enabled = enabled && setupCompleted,
            r1Hour = r1H,
            r1Minute = r1M,
            r2Hour = r2H,
            r2Minute = r2M,
            userId = uid,
            minAdvanceSecondsSlot1 = if (activeSlot == 1) 120L else 0L,
            minAdvanceSecondsSlot2 = if (activeSlot == 2) 120L else 0L,
            now = now
        )

        if (!enabled || !setupCompleted) return@withLock false
        val slot = activeSlot ?: return@withLock false
        if (slotOverride == null && !allowCatchUp) return@withLock false

        val todayKey = now.toLocalDate().toString()
        val todayCount = db.dailyCountDao().getDay(uid, todayKey)?.count ?: 0L

        // Correct condition: NO reminder if today's daily target is already complete
        if (!isTargetIncomplete(todayCount, dailyTarget)) {
            markDueSlotConsumedForToday(appContext, slot, now)
            return@withLock false
        }

        ensureNotificationChannel(appContext)
        if (!areNotificationsAllowed(appContext)) return@withLock false

        // Atomic per-slot + max-2-per-day check before notifying
        if (!canSendSlotTodayAndIncrement(appContext, todayKey, slot)) {
            markDueSlotConsumedForToday(appContext, slot, now)
            return@withLock false
        }

        val formatter = NumberFormat.getNumberInstance(Locale.US)
        val targetStr = formatter.format(dailyTarget)
        val todayStr = formatter.format(todayCount)
        val remainingStr = formatter.format((dailyTarget - todayCount).coerceAtLeast(0L))

        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            slot,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = "Today's target of $targetStr is not complete yet ($todayStr / $targetStr, $remainingStr remaining)."
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_one_crore)
            .setContentTitle("1 Crore — Daily Target")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        return@withLock try {
            NotificationManagerCompat.from(appContext).notify(2000 + slot, notification)
            markDueSlotConsumedForToday(appContext, slot, now)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private suspend fun findDueCatchUpSlot(context: Context, now: ZonedDateTime): Int? {
        val prefs = context.getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
        val nowMillis = now.toInstant().toEpochMilli()
        val todayKey = now.toLocalDate().toString()
        val dataPrefs = context.reminderPrefs.data.first()

        val due1 = prefs.getLong(KEY_DUE_SLOT_1_MILLIS, 0L)
        if (due1 in 1L..nowMillis && isSameLocalDate(due1, now) &&
            dataPrefs[KEY_LAST_SENT_SLOT_1_DATE] != todayKey
        ) {
            return 1
        }
        val due2 = prefs.getLong(KEY_DUE_SLOT_2_MILLIS, 0L)
        if (due2 in 1L..nowMillis && isSameLocalDate(due2, now) &&
            dataPrefs[KEY_LAST_SENT_SLOT_2_DATE] != todayKey
        ) {
            return 2
        }
        return null
    }

    private fun markDueSlotConsumedForToday(context: Context, slot: Int, now: ZonedDateTime) {
        val tomorrowMillis = now.plusDays(1).toInstant().toEpochMilli()
        val key = if (slot == 1) KEY_DUE_SLOT_1_MILLIS else KEY_DUE_SLOT_2_MILLIS
        context.getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(key, tomorrowMillis)
            .apply()
    }

    suspend fun canSendSlotTodayAndIncrement(
        context: Context,
        todayKey: String = LocalDate.now().toString(),
        slot: Int
    ): Boolean {
        val prefs = context.reminderPrefs.data.first()
        val slotKey = if (slot == 1) KEY_LAST_SENT_SLOT_1_DATE else KEY_LAST_SENT_SLOT_2_DATE
        if (prefs[slotKey] == todayKey) {
            return false
        }
        val storedDate = prefs[KEY_SENT_DATE] ?: ""
        val currentCount = if (storedDate == todayKey) prefs[KEY_SENT_COUNT] ?: 0 else 0
        if (currentCount >= MAX_NOTIFICATIONS_PER_DAY) return false

        context.reminderPrefs.edit { mutable ->
            mutable[KEY_SENT_DATE] = todayKey
            mutable[KEY_SENT_COUNT] = currentCount + 1
            mutable[slotKey] = todayKey
        }
        return true
    }

    suspend fun canSendTodayAndIncrement(context: Context, todayKey: String = LocalDate.now().toString()): Boolean {
        val prefs = context.reminderPrefs.data.first()
        val storedDate = prefs[KEY_SENT_DATE] ?: ""
        val currentCount = if (storedDate == todayKey) prefs[KEY_SENT_COUNT] ?: 0 else 0
        if (currentCount >= MAX_NOTIFICATIONS_PER_DAY) return false

        context.reminderPrefs.edit { mutable ->
            mutable[KEY_SENT_DATE] = todayKey
            mutable[KEY_SENT_COUNT] = currentCount + 1
        }
        return true
    }

    suspend fun resetForTest(context: Context) {
        context.applicationContext
            .getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.applicationContext.reminderPrefs.edit { it.clear() }
    }
}

class ReminderCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val slotRaw = inputData.getInt(ReminderScheduler.EXTRA_SLOT, -1)
        val slotOverride = slotRaw.takeIf { it == 1 || it == 2 }
        ReminderScheduler.evaluateAndSendReminder(
            context = applicationContext,
            slotOverride = slotOverride,
            allowCatchUp = true
        )
        return Result.success()
    }
}
