package com.example.announcements

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.DuroodRepository
import com.example.data.local.AppDatabase
import com.example.widget.WidgetAndFloatingBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ScheduledAnnouncementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AnnouncementScheduledDispatcher.ACTION_DISPATCH_SCHEDULED_ANNOUNCEMENT) {
            return
        }
        val announcementId = intent.getStringExtra(AnnouncementScheduledDispatcher.EXTRA_SCHEDULED_ANNOUNCEMENT_ID)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AnnouncementScheduledDispatcher.dispatchDueAnnouncements(
                    context = context.applicationContext,
                    specificAnnouncementId = announcementId
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object AnnouncementScheduledDispatcher {
    const val ACTION_DISPATCH_SCHEDULED_ANNOUNCEMENT =
        "com.example.announcements.ACTION_DISPATCH_SCHEDULED_ANNOUNCEMENT"
    const val EXTRA_SCHEDULED_ANNOUNCEMENT_ID = "extra_scheduled_announcement_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _dueAnnouncementEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val dueAnnouncementEvents: SharedFlow<String> = _dueAnnouncementEvents.asSharedFlow()

    fun scheduleOrDispatchAnnouncement(
        context: Context,
        item: AnnouncementItem,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val appContext = context.applicationContext
        AnnouncementScheduleStore.saveAnnouncement(appContext, item)
        val triggerAt = item.effectiveStartAtMillis
        if (triggerAt <= nowMillis) {
            scope.launch {
                dispatchDueAnnouncements(
                    context = appContext,
                    nowMillis = nowMillis,
                    specificAnnouncementId = item.id
                )
            }
        } else {
            // R4-D1: exactly ONE trigger — the AlarmManager alarm. It fires whether
            // or not the app is alive (the receiver dispatches), so a second
            // in-process timer only created a double-dispatch race.
            scheduleAlarmManagerTrigger(appContext, item.id, triggerAt)
        }
    }

    fun reschedulePendingAnnouncements(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val appContext = context.applicationContext
        // R3-P2-44: drop announcements whose window already ended, so the
        // store does not keep dead items across restarts.
        AnnouncementScheduleStore.deleteExpiredAnnouncements(appContext, nowMillis)
        val all = AnnouncementScheduleStore.getAllAnnouncements(appContext)
        var hasDue = false
        for (item in all) {
            if (AnnouncementScheduleStore.wasPushDispatched(appContext, item.id)) continue
            if (nowMillis > item.endAtMillis) continue
            val triggerAt = item.effectiveStartAtMillis
            if (triggerAt <= nowMillis) {
                hasDue = true
            } else {
                // R4-D1: exactly ONE trigger — the AlarmManager alarm (see above).
                scheduleAlarmManagerTrigger(appContext, item.id, triggerAt)
            }
        }
        // R3-P2-43: ONE dispatch run for all due items, not one coroutine
        // per item. The per-item launches each re-read the whole DB and
        // raced on the dispatched flag, so two runs could both post and
        // double-count against the daily cap. A single call iterates the due
        // candidates in order, with one audience resolution and sequential
        // cap accounting.
        if (hasDue) {
            scope.launch {
                dispatchDueAnnouncements(context = appContext, nowMillis = nowMillis)
            }
        }
    }

    private fun scheduleAlarmManagerTrigger(
        context: Context,
        announcementId: String,
        triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduledAnnouncementReceiver::class.java).apply {
            action = ACTION_DISPATCH_SCHEDULED_ANNOUNCEMENT
            putExtra(EXTRA_SCHEDULED_ANNOUNCEMENT_ID, announcementId)
        }
        val requestCode = ((announcementId.hashCode() and 0x0FFFFFFF) + 7000)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            runCatching {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    suspend fun resolveAudienceContextFromDatabase(
        context: Context,
        db: AppDatabase = AppDatabase.getInstance(context),
        userIdOverride: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        isAppInForegroundSession: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<String, AnnouncementAudienceContext> {
        val appContext = context.applicationContext
        val userId = WidgetAndFloatingBridge.resolveUserId(appContext, db, userIdOverride)
        val todayKey = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().toString()
        val profile = runCatching { db.userProfileDao().getProfile(userId) }.getOrNull()
        val allDays = runCatching { db.dailyCountDao().getAllDays(userId) }.getOrDefault(emptyList())
        val todayCount = allDays.firstOrNull { it.dateKey == todayKey }?.count ?: 0L
        val dailyTarget = profile?.dailyTarget?.coerceAtLeast(1) ?: DuroodRepository.DEFAULT_DAILY_TARGET

        val activePastDays = allDays.filter { it.count > 0L && it.dateKey != todayKey }
        val latestActiveDay = allDays.filter { it.count > 0L }.maxByOrNull { it.dateKey }
        val latestPastDayMillis = activePastDays.maxOfOrNull { day ->
            val dateStartMillis = runCatching {
                LocalDate.parse(day.dateKey).atStartOfDay(zoneId).toInstant().toEpochMilli()
            }.getOrDefault(0L)
            maxOf(day.updatedAtMillis, dateStartMillis)
        } ?: 0L

        val storedAppOpenMillis = AnnouncementFrequencyStore.getEffectiveLastActiveAtMillis(
            context = appContext,
            userId = userId,
            isAppInForegroundSession = isAppInForegroundSession
        )
        val cachedAudience = AnnouncementFrequencyStore.readCachedAudienceContext(appContext, userId)

        val resolvedLastActiveMillis = when {
            todayCount > 0L -> nowMillis
            storedAppOpenMillis > 0L && latestPastDayMillis > 0L -> maxOf(storedAppOpenMillis, latestPastDayMillis)
            storedAppOpenMillis > 0L -> storedAppOpenMillis
            latestPastDayMillis > 0L -> latestPastDayMillis
            cachedAudience != null && cachedAudience.lastActiveAtMillis > 0L -> cachedAudience.lastActiveAtMillis
            else -> 0L
        }

        val resolvedContext = AnnouncementAudienceContext(
            todayCount = todayCount,
            dailyTarget = dailyTarget,
            lastActiveAtMillis = resolvedLastActiveMillis,
            latestActiveDateKey = latestActiveDay?.dateKey ?: cachedAudience?.latestActiveDateKey
        )
        AnnouncementFrequencyStore.cacheAudienceContext(appContext, userId, resolvedContext)
        return userId to resolvedContext
    }

    suspend fun dispatchDueAnnouncements(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        specificAnnouncementId: String? = null,
        db: AppDatabase = AppDatabase.getInstance(context),
        userIdOverride: String? = null
    ): Int {
        val appContext = context.applicationContext
        val all = AnnouncementScheduleStore.getAllAnnouncements(appContext)
        val dueCandidates = all.filter { item ->
            (specificAnnouncementId == null || item.id == specificAnnouncementId) &&
                !AnnouncementScheduleStore.wasPushDispatched(appContext, item.id) &&
                nowMillis in item.effectiveStartAtMillis..item.endAtMillis
        }
        if (dueCandidates.isEmpty()) return 0

        val (userId, audienceContext) = resolveAudienceContextFromDatabase(
            context = appContext,
            db = db,
            userIdOverride = userIdOverride,
            nowMillis = nowMillis,
            isAppInForegroundSession = false
        )

        var dispatchedCount = 0
        for (item in dueCandidates.sortedWith(
            compareByDescending<AnnouncementItem> { it.isUrgent }
                .thenByDescending { it.priority }
                .thenByDescending { it.effectiveStartAtMillis }
                .thenBy { it.id }
        )) {
            if (!AnnouncementFrequencyStore.isEligibleForPush(
                    context = appContext,
                    userId = userId,
                    announcement = item,
                    nowMillis = nowMillis,
                    audienceContext = audienceContext
                )
            ) {
                // R3-P2-38: do NOT mark dispatched on eligibility failure —
                // the reason is often transient (daily cap, 10-min gap), and
                // marking here lost the push forever. The item stays due and
                // is re-evaluated on the next cycle.
                continue
            }

            val posted = AnnouncementNotificationHelper.showAnnouncementPushNotification(
                context = appContext,
                title = item.title,
                body = item.body,
                announcementId = item.id,
                url = item.buttonUrl,
                isUrgent = item.isUrgent
            )
            if (posted) {
                // R3-P2-38: mark dispatched only once the push was actually
                // posted — a failed post (e.g. permission denied) must not
                // consume the announcement.
                AnnouncementScheduleStore.markPushDispatched(appContext, item.id)
                if (!item.isUrgent) {
                    AnnouncementFrequencyStore.recordPushSent(appContext, userId, nowMillis)
                }
                AnnouncementHistoryStore.recordAnnouncement(
                    context = appContext,
                    userId = userId,
                    announcement = item,
                    status = AnnouncementHistoryStore.STATUS_PUSHED,
                    recordedAtMillis = nowMillis
                )
            }
            _dueAnnouncementEvents.tryEmit(item.id)
            if (posted) dispatchedCount++
        }
        return dispatchedCount
    }

    fun resetForTest(context: Context) {
        AnnouncementScheduleStore.resetForTest(context)
    }
}
