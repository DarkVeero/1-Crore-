package com.example.announcements

import android.Manifest
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
import com.example.MainActivity
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AnnouncementLaunchRequest(
    val announcementId: String,
    val fallbackTitle: String? = null,
    val fallbackBody: String? = null,
    val fallbackUrl: String? = null,
    val fallbackIsUrgent: Boolean = false
)

object AnnouncementNotificationHelper {
    const val TOPIC_ANNOUNCEMENTS = "announcements"
    const val CHANNEL_ID = "one_crore_announcements"
    const val EXTRA_FROM_ANNOUNCEMENT_NOTIFICATION = "extra_from_announcement_notification"
    const val EXTRA_ANNOUNCEMENT_ID = "extra_announcement_id"
    const val EXTRA_ANNOUNCEMENT_TITLE = "extra_announcement_title"
    const val EXTRA_ANNOUNCEMENT_BODY = "extra_announcement_body"
    const val EXTRA_ANNOUNCEMENT_URL = "extra_announcement_url"
    const val EXTRA_ANNOUNCEMENT_URGENT = "extra_announcement_urgent"

    private const val PREFS_NAME = "one_crore_announcement_push_prefs"
    private const val KEY_TOPIC_SUBSCRIBED = "topic_announcements_subscribed"
    private const val KEY_PERMISSION_REQUESTED = "post_notifications_permission_requested"
    // R4-D11: retries for a failed topic subscribe (30s, 2m, 5m). Before this,
    // a failure was swallowed entirely and the next attempt only happened on
    // the next Home open.
    private const val MAX_TOPIC_SUBSCRIBE_RETRIES = 3
    private val topicRetryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.announcement_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.announcement_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun ensureSubscribedToAnnouncementsTopic(context: Context) {
        subscribeToAnnouncementsTopic(context, attempt = 0)
    }

    private fun subscribeToAnnouncementsTopic(context: Context, attempt: Int) {
        val appContext = context.applicationContext
        // Already subscribed (and recorded) — no need to subscribe again.
        if (isTopicSubscribedRecorded(appContext)) return
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            FirebaseMessaging.getInstance()
                .subscribeToTopic(TOPIC_ANNOUNCEMENTS)
                .addOnSuccessListener {
                    markTopicSubscribed(appContext)
                }
                .addOnFailureListener {
                    scheduleTopicSubscribeRetry(appContext, attempt)
                }
        } catch (_: Exception) {
            // Safe no-op in headless unit test environments without Play Services
            scheduleTopicSubscribeRetry(appContext, attempt)
        }
    }

    private fun scheduleTopicSubscribeRetry(context: Context, failedAttempt: Int) {
        val nextAttempt = failedAttempt + 1
        if (nextAttempt > MAX_TOPIC_SUBSCRIBE_RETRIES) return
        val delayMillis = when (nextAttempt) {
            1 -> 30_000L
            2 -> 120_000L
            else -> 300_000L
        }
        topicRetryScope.launch {
            delay(delayMillis)
            subscribeToAnnouncementsTopic(context, nextAttempt)
        }
    }

    fun isTopicSubscribedRecorded(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TOPIC_SUBSCRIBED, false)
    }

    internal fun markTopicSubscribed(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TOPIC_SUBSCRIBED, true)
            .apply()
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun shouldRequestNotificationPermissionOnHomeOpen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (canPostNotifications(context)) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)
    }

    fun markNotificationPermissionRequested(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSION_REQUESTED, true)
            .apply()
    }

    fun extractLaunchRequestFromIntent(intent: Intent?): AnnouncementLaunchRequest? {
        if (intent == null) return null
        val extras = intent.extras ?: return null
        val type = extras.getString("type")?.trim()
        val fromExtraFlag = extras.getBoolean(EXTRA_FROM_ANNOUNCEMENT_NOTIFICATION, false)
        val rawId = (
            extras.getString(EXTRA_ANNOUNCEMENT_ID)
                ?: extras.getString("announcementId")
                ?: extras.getString("announcement_id")
                ?: extras.getString("id")
            )?.trim()?.takeIf { it.isNotEmpty() }

        val title = (
            extras.getString(EXTRA_ANNOUNCEMENT_TITLE)
                ?: extras.getString("title")
                ?: extras.getString("gcm.notification.title")
            )?.trim()?.takeIf { it.isNotEmpty() }

        val body = (
            extras.getString(EXTRA_ANNOUNCEMENT_BODY)
                ?: extras.getString("body")
                ?: extras.getString("message")
                ?: extras.getString("gcm.notification.body")
            )?.trim()?.takeIf { it.isNotEmpty() }

        val url = (
            extras.getString(EXTRA_ANNOUNCEMENT_URL)
                ?: extras.getString("buttonUrl")
                ?: extras.getString("url")
                ?: extras.getString("link")
            )?.trim()?.takeIf {
                it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("http://", ignoreCase = true)
            }

        val isUrgentRaw = extras.getBoolean(EXTRA_ANNOUNCEMENT_URGENT, false) ||
            extras.getString("isUrgent")?.trim()?.equals("true", ignoreCase = true) == true ||
            extras.getString("urgent")?.trim()?.equals("true", ignoreCase = true) == true

        val isAnnouncementType = type.equals("announcement", ignoreCase = true)
        if (!fromExtraFlag && !isAnnouncementType && rawId == null) {
            return null
        }

        val effectiveId = rawId ?: when {
            title != null || body != null -> "push_${("${title.orEmpty()}:${body.orEmpty()}").hashCode()}"
            else -> return null
        }

        return AnnouncementLaunchRequest(
            announcementId = effectiveId,
            fallbackTitle = title,
            fallbackBody = body,
            fallbackUrl = url,
            fallbackIsUrgent = isUrgentRaw
        )
    }

    fun buildLaunchAppPendingIntent(
        context: Context,
        announcementId: String? = null,
        title: String? = null,
        body: String? = null,
        url: String? = null,
        isUrgent: Boolean = false
    ): PendingIntent {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_FROM_ANNOUNCEMENT_NOTIFICATION, true)
            if (!announcementId.isNullOrBlank()) {
                putExtra(EXTRA_ANNOUNCEMENT_ID, announcementId)
            }
            if (!title.isNullOrBlank()) {
                putExtra(EXTRA_ANNOUNCEMENT_TITLE, title)
            }
            if (!body.isNullOrBlank()) {
                putExtra(EXTRA_ANNOUNCEMENT_BODY, body)
            }
            if (!url.isNullOrBlank()) {
                putExtra(EXTRA_ANNOUNCEMENT_URL, url)
            }
            if (isUrgent) {
                putExtra(EXTRA_ANNOUNCEMENT_URGENT, true)
            }
        }
        val seed = announcementId ?: "${title.orEmpty()}:${body.orEmpty()}"
        val requestCode = (seed.hashCode() and 0x7FFFFFFF).coerceAtLeast(1)
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showAnnouncementPushNotification(
        context: Context,
        title: String,
        body: String,
        announcementId: String? = null,
        url: String? = null,
        isUrgent: Boolean = false
    ): Boolean {
        val cleanTitle = title.trim().ifBlank { context.getString(R.string.app_name) }
        val cleanBody = body.trim()
        if (cleanBody.isEmpty() && title.isBlank()) return false

        ensureNotificationChannel(context)
        if (!canPostNotifications(context)) return false

        val pendingIntent = buildLaunchAppPendingIntent(
            context = context,
            announcementId = announcementId,
            title = cleanTitle,
            body = cleanBody,
            url = url,
            isUrgent = isUrgent
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(cleanTitle)
            .setContentText(cleanBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = ((announcementId ?: "$cleanTitle:$cleanBody").hashCode() and 0x0FFFFFFF) + 4000
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    suspend fun handleIncomingFcmAnnouncement(
        context: Context,
        item: AnnouncementItem,
        nowMillis: Long = System.currentTimeMillis(),
        userIdOverride: String? = null,
        audienceContextOverride: AnnouncementAudienceContext? = null
    ): Boolean {
        val appContext = context.applicationContext
        // R4-D6: do NOT save the announcement before the decision. Saving first
        // persisted announcements that were never eligible (wrong audience) or
        // already done (milestone), so they lingered in the store and could be
        // re-dispatched later when eligibility changed. The future path saves
        // via scheduleOrDispatchAnnouncement; the show path saves below.
        if (item.isScheduledForFuture(nowMillis)) {
            AnnouncementScheduledDispatcher.scheduleOrDispatchAnnouncement(
                context = appContext,
                item = item,
                nowMillis = nowMillis
            )
            return false
        }

        val (resolvedUserId, resolvedAudience) = if (userIdOverride != null && audienceContextOverride != null) {
            userIdOverride to audienceContextOverride
        } else {
            val (dbUserId, dbAudience) = AnnouncementScheduledDispatcher.resolveAudienceContextFromDatabase(
                context = appContext,
                userIdOverride = userIdOverride,
                nowMillis = nowMillis,
                isAppInForegroundSession = false
            )
            dbUserId to (audienceContextOverride ?: dbAudience)
        }

        if (MilestoneAnnouncementManager.isMilestoneAnnouncementId(item.id) &&
            (AnnouncementScheduleStore.wasPushDispatched(appContext, item.id) ||
                MilestoneAnnouncementManager.isMilestoneCompletedForUser(appContext, resolvedUserId, item.id))
        ) {
            return false
        }

        if (!AnnouncementFrequencyStore.isEligibleForPush(
                context = appContext,
                userId = resolvedUserId,
                announcement = item,
                nowMillis = nowMillis,
                audienceContext = resolvedAudience
            )
        ) {
            // R3-P2-38: do NOT mark dispatched on eligibility failure — the
            // reason is often transient (daily cap, 10-min gap). Marking here
            // lost the push forever when notification permission was later
            // granted or the cap reset. The item is not in the store on this
            // path, so nothing re-dispatches it spuriously.
            return false
        }

        // R4-D6: only now — the announcement passed every check and is about
        // to be shown — does it belong in the store.
        AnnouncementScheduleStore.saveAnnouncement(appContext, item)
        val shown = showAnnouncementPushNotification(
            context = appContext,
            title = item.title,
            body = item.body,
            announcementId = item.id,
            url = item.buttonUrl,
            isUrgent = item.isUrgent
        )
        if (shown) {
            // R3-P2-38: mark dispatched only once the push was actually
            // posted — a failed post (e.g. permission denied) must not
            // consume the announcement.
            AnnouncementScheduleStore.markPushDispatched(appContext, item.id)
            if (!item.isUrgent) {
                AnnouncementFrequencyStore.recordPushSent(appContext, resolvedUserId, nowMillis)
            }
            AnnouncementHistoryStore.recordAnnouncement(
                context = appContext,
                userId = resolvedUserId,
                announcement = item,
                status = AnnouncementHistoryStore.STATUS_PUSHED,
                recordedAtMillis = nowMillis
            )
        }
        return shown
    }

    fun resetForTest(context: Context) {
        AnnouncementScheduledDispatcher.resetForTest(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
