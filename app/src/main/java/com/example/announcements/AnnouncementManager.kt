package com.example.announcements

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

enum class AnnouncementAction(val wireValue: String) {
    NONE("none"),
    UPDATE("update"),
    SHARE("share"),
    LINK("link");

    companion object {
        fun fromString(raw: String?): AnnouncementAction {
            return when (raw?.trim()?.lowercase(Locale.US)) {
                "update" -> UPDATE
                "share" -> SHARE
                "link", "url" -> LINK
                else -> NONE
            }
        }
    }
}

enum class AnnouncementAudience(val wireValue: String) {
    ALL("all"),
    INCOMPLETE_DAILY_TARGET("incomplete_today"),
    INACTIVE_DAYS("inactive_days");

    companion object {
        fun fromString(raw: String?): AnnouncementAudience {
            return when (raw?.trim()?.lowercase(Locale.US)) {
                "incomplete_today",
                "incomplete_daily_target",
                "incomplete_target",
                "target_incomplete",
                "not_completed_today" -> INCOMPLETE_DAILY_TARGET

                "inactive_days",
                "inactive",
                "not_opened_in_days",
                "inactive_for_days" -> INACTIVE_DAYS

                else -> ALL
            }
        }
    }
}

data class AnnouncementAudienceContext(
    val todayCount: Long = 0L,
    val dailyTarget: Int = 2600,
    val lastActiveAtMillis: Long = 0L,
    val latestActiveDateKey: String? = null
)

object AnnouncementOwnerConfig {
    private const val PREFS_NAME = "one_crore_owner_prefs"
    private const val KEY_OWNER_UNLOCKED = "owner_tools_unlocked"

    val DEFAULT_OWNER_EMAILS = setOf(
        "1croresupport@gmail.com",
        "mrmudasir1269@gmail.com"
    )

    @Volatile
    var ownerOverrideForTest: Boolean? = null

    fun isOwnerAccount(email: String?): Boolean {
        ownerOverrideForTest?.let { return it }
        val clean = email?.trim()?.lowercase(Locale.US).orEmpty()
        return clean in DEFAULT_OWNER_EMAILS
    }

    fun isOwnerUnlocked(context: Context, email: String?): Boolean {
        ownerOverrideForTest?.let { return it }
        if (isOwnerAccount(email)) return true
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OWNER_UNLOCKED, false)
    }

    fun setOwnerUnlocked(context: Context, unlocked: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OWNER_UNLOCKED, unlocked)
            .apply()
    }

    fun resetForTest(context: Context) {
        ownerOverrideForTest = null
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}

data class AnnouncementItem(
    val id: String,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val buttonText: String? = null,
    val buttonAction: AnnouncementAction = AnnouncementAction.NONE,
    val buttonUrl: String? = null,
    val startAtMillis: Long = 0L,
    val endAtMillis: Long = Long.MAX_VALUE,
    val maxShowsPerDay: Int = DEFAULT_MAX_SHOWS_PER_DAY,
    val maxShowsPerWeek: Int = DEFAULT_MAX_SHOWS_PER_WEEK,
    val priority: Int = 0,
    val scheduledAtMillis: Long = 0L,
    val targetAudience: AnnouncementAudience = AnnouncementAudience.ALL,
    val inactiveDaysThreshold: Int = DEFAULT_INACTIVE_DAYS_THRESHOLD,
    val isUrgent: Boolean = false,
    val cloudPublished: Boolean = true
) {
    val hasActionButton: Boolean
        get() = !buttonText.isNullOrBlank() &&
            buttonAction != AnnouncementAction.NONE &&
            (buttonAction != AnnouncementAction.LINK || !buttonUrl.isNullOrBlank())

    val effectiveStartAtMillis: Long
        get() = maxOf(startAtMillis, scheduledAtMillis)

    fun isScheduledForFuture(nowMillis: Long): Boolean {
        return effectiveStartAtMillis > nowMillis
    }

    fun toFirestoreMap(nowMillis: Long = System.currentTimeMillis()): Map<String, Any?> {
        val effectiveStart = effectiveStartAtMillis.takeIf { it > 0L } ?: nowMillis
        val effectiveEnd = if (endAtMillis == Long.MAX_VALUE) {
            effectiveStart + 7L * ONE_DAY_MILLIS
        } else {
            endAtMillis
        }
        val map = mutableMapOf<String, Any?>(
            "id" to id,
            "active" to true,
            "title" to title,
            "body" to body,
            "startAt" to com.google.firebase.Timestamp(Date(effectiveStart)),
            "startAtMillis" to effectiveStart,
            "endAt" to com.google.firebase.Timestamp(Date(effectiveEnd)),
            "endAtMillis" to effectiveEnd,
            "maxShowsPerDay" to maxShowsPerDay,
            "maxShowsPerWeek" to maxShowsPerWeek,
            "priority" to priority,
            "targetAudience" to targetAudience.wireValue,
            "inactiveDays" to inactiveDaysThreshold.coerceAtLeast(1),
            "isUrgent" to isUrgent
        )
        if (scheduledAtMillis > 0L) {
            map["scheduledAt"] = com.google.firebase.Timestamp(Date(scheduledAtMillis))
            map["scheduledAtMillis"] = scheduledAtMillis
            map["dispatched"] = scheduledAtMillis <= nowMillis
        } else {
            map["dispatched"] = true
        }
        if (!imageUrl.isNullOrBlank()) {
            map["imageUrl"] = imageUrl
        }
        if (hasActionButton) {
            map["buttonText"] = buttonText
            map["buttonAction"] = buttonAction.wireValue
            if (!buttonUrl.isNullOrBlank()) {
                map["buttonUrl"] = buttonUrl
            }
            map["button"] = buildMap<String, Any?> {
                put("text", buttonText)
                put("action", buttonAction.wireValue)
                if (!buttonUrl.isNullOrBlank()) {
                    put("url", buttonUrl)
                }
            }
        }
        return map
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("body", body)
            if (!imageUrl.isNullOrBlank()) put("imageUrl", imageUrl)
            if (!buttonText.isNullOrBlank()) put("buttonText", buttonText)
            put("buttonAction", buttonAction.wireValue)
            if (!buttonUrl.isNullOrBlank()) put("buttonUrl", buttonUrl)
            put("startAtMillis", startAtMillis)
            put("endAtMillis", endAtMillis)
            put("maxShowsPerDay", maxShowsPerDay)
            put("maxShowsPerWeek", maxShowsPerWeek)
            put("priority", priority)
            put("scheduledAtMillis", scheduledAtMillis)
            put("targetAudience", targetAudience.wireValue)
            put("inactiveDaysThreshold", inactiveDaysThreshold.coerceAtLeast(1))
            put("isUrgent", isUrgent)
            put("cloudPublished", cloudPublished)
        }
    }

    companion object {
        const val MIN_CONFIGURABLE_DAILY_CAP = 3
        const val MAX_CONFIGURABLE_DAILY_CAP = 5
        const val DEFAULT_MAX_SHOWS_PER_DAY = 3
        const val DEFAULT_MAX_SHOWS_PER_WEEK = 21
        const val MIN_SHOW_INTERVAL_MILLIS = 10L * 60L * 1000L
        const val DEFAULT_INACTIVE_DAYS_THRESHOLD = 3
        const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L

        fun fromJsonObject(obj: JSONObject): AnnouncementItem? {
            val id = obj.optString("id").trim()
            if (id.isEmpty()) return null
            val cloudPublished = obj.optBoolean("cloudPublished", true)
            val dataMap = mutableMapOf<String, Any?>(
                "title" to obj.optString("title", ""),
                "body" to obj.optString("body", ""),
                "imageUrl" to obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                "buttonText" to obj.optString("buttonText", "").takeIf { it.isNotBlank() },
                "buttonAction" to obj.optString("buttonAction", "none"),
                "buttonUrl" to obj.optString("buttonUrl", "").takeIf { it.isNotBlank() },
                "startAtMillis" to obj.optLong("startAtMillis", 0L),
                "endAtMillis" to obj.optLong("endAtMillis", Long.MAX_VALUE),
                "maxShowsPerDay" to obj.optInt("maxShowsPerDay", DEFAULT_MAX_SHOWS_PER_DAY),
                "maxShowsPerWeek" to obj.optInt("maxShowsPerWeek", DEFAULT_MAX_SHOWS_PER_WEEK),
                "priority" to obj.optInt("priority", 0),
                "scheduledAtMillis" to obj.optLong("scheduledAtMillis", 0L),
                "targetAudience" to obj.optString("targetAudience", AnnouncementAudience.ALL.wireValue),
                "inactiveDaysThreshold" to obj.optInt("inactiveDaysThreshold", DEFAULT_INACTIVE_DAYS_THRESHOLD),
                "isUrgent" to obj.optBoolean("isUrgent", false)
            )
            val item = AnnouncementParser.parseAnnouncementDocument(
                id = id,
                data = dataMap,
                allowInactiveOrExpired = true
            ) ?: return null
            return item.copy(cloudPublished = cloudPublished)
        }
    }
}

object AnnouncementParser {

    fun parseAnnouncementDocument(
        id: String,
        data: Map<String, Any?>?,
        allowInactiveOrExpired: Boolean = false
    ): AnnouncementItem? {
        if (id.isBlank() || data == null || data.isEmpty()) return null

        val activeFlag = data["active"] ?: data["isActive"] ?: data["enabled"]
        val isExplicitlyInactive = when (activeFlag) {
            is Boolean -> !activeFlag
            is Number -> activeFlag.toInt() == 0
            is String -> {
                val clean = activeFlag.trim().lowercase(Locale.US)
                clean == "false" || clean == "0" || clean == "no"
            }
            else -> false
        }
        if (isExplicitlyInactive && !allowInactiveOrExpired) return null

        val title = (data["title"] as? String ?: data["heading"] as? String)?.trim().orEmpty()
        val body = (
            data["body"] as? String
                ?: data["message"] as? String
                ?: data["description"] as? String
            )?.trim().orEmpty()
        if (title.isEmpty() && body.isEmpty()) return null

        val rawImageUrl = (data["imageUrl"] as? String)
            ?: (data["image_url"] as? String)
            ?: (data["pictureUrl"] as? String)
            ?: (data["picture_url"] as? String)
            ?: (data["bannerUrl"] as? String)
            ?: (data["banner_url"] as? String)
            ?: (data["image"] as? String)
        val cleanImageUrl = rawImageUrl?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }

        val buttonMap = data["button"] as? Map<*, *>
        val rawButtonText = (buttonMap?.get("text") as? String)
            ?: (buttonMap?.get("label") as? String)
            ?: (data["buttonText"] as? String)
            ?: (data["button_text"] as? String)
        val cleanButtonText = rawButtonText?.trim()?.takeIf { it.isNotEmpty() }

        val rawButtonAction = (buttonMap?.get("action") as? String)
            ?: (buttonMap?.get("type") as? String)
            ?: (data["buttonAction"] as? String)
            ?: (data["button_action"] as? String)
            ?: (data["action"] as? String)

        val rawButtonUrl = (buttonMap?.get("url") as? String)
            ?: (buttonMap?.get("link") as? String)
            ?: (data["buttonUrl"] as? String)
            ?: (data["button_url"] as? String)
            ?: (data["linkUrl"] as? String)
            ?: (data["url"] as? String)
            ?: (data["link"] as? String)
        val cleanButtonUrl = rawButtonUrl?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }

        val effectiveButtonText = when {
            cleanButtonText != null -> cleanButtonText
            cleanButtonUrl != null && (rawButtonAction.isNullOrBlank() || AnnouncementAction.fromString(rawButtonAction) == AnnouncementAction.LINK) -> "Join"
            else -> null
        }

        val parsedAction = when {
            effectiveButtonText == null -> AnnouncementAction.NONE
            rawButtonAction.isNullOrBlank() && cleanButtonUrl != null -> AnnouncementAction.LINK
            else -> AnnouncementAction.fromString(rawButtonAction)
        }

        val effectiveAction = if (parsedAction == AnnouncementAction.LINK && cleanButtonUrl == null) {
            AnnouncementAction.NONE
        } else {
            parsedAction
        }

        val scheduledAtMillis = parseTimestampMillis(
            data["scheduledAt"]
                ?: data["scheduledAtMillis"]
                ?: data["scheduled_at"]
                ?: data["sendAt"]
                ?: data["send_at"],
            defaultMillis = 0L
        )
        val rawStartAtMillis = parseTimestampMillis(
            data["startAt"] ?: data["startAtMillis"] ?: data["start_at"],
            defaultMillis = 0L
        )
        val startAtMillis = if (rawStartAtMillis > 0L) rawStartAtMillis else scheduledAtMillis
        val effectiveStartMillis = maxOf(startAtMillis, scheduledAtMillis)

        val endAtMillis = parseTimestampMillis(
            data["endAt"] ?: data["endAtMillis"] ?: data["end_at"],
            defaultMillis = Long.MAX_VALUE
        )
        if (!allowInactiveOrExpired && endAtMillis < effectiveStartMillis) return null

        val maxShowsPerDay = parsePositiveInt(
            data["maxShowsPerDay"] ?: data["max_shows_per_day"],
            defaultVal = AnnouncementFrequencyStore.configuredMaxShowsPerDay
        )
        val maxShowsPerWeek = parsePositiveInt(
            data["maxShowsPerWeek"] ?: data["max_shows_per_week"],
            defaultVal = AnnouncementItem.DEFAULT_MAX_SHOWS_PER_WEEK
        )
        val priority = parseSignedInt(data["priority"], defaultVal = 0)

        val targetMap = (data["targeting"] as? Map<*, *>) ?: (data["audience"] as? Map<*, *>)
        val rawAudience = (targetMap?.get("type") as? String)
            ?: (targetMap?.get("targetAudience") as? String)
            ?: (data["targetAudience"] as? String)
            ?: (data["target_audience"] as? String)
            ?: (data["audience"] as? String)
            ?: (data["targetType"] as? String)
            ?: (data["target_type"] as? String)
        val targetAudience = AnnouncementAudience.fromString(rawAudience)

        val inactiveDaysThreshold = parsePositiveInt(
            targetMap?.get("inactiveDays")
                ?: targetMap?.get("inactiveDaysThreshold")
                ?: targetMap?.get("days")
                ?: data["inactiveDays"]
                ?: data["inactive_days"]
                ?: data["inactiveDaysThreshold"]
                ?: data["inactive_days_threshold"],
            defaultVal = AnnouncementItem.DEFAULT_INACTIVE_DAYS_THRESHOLD
        )

        val isUrgent = parseBooleanFlag(
            data["isUrgent"] ?: data["urgent"] ?: data["is_urgent"],
            defaultVal = false
        )

        return AnnouncementItem(
            id = id,
            title = title,
            body = body,
            imageUrl = cleanImageUrl,
            buttonText = if (effectiveAction == AnnouncementAction.NONE) null else effectiveButtonText,
            buttonAction = effectiveAction,
            buttonUrl = cleanButtonUrl,
            startAtMillis = startAtMillis,
            endAtMillis = endAtMillis,
            maxShowsPerDay = maxShowsPerDay,
            maxShowsPerWeek = maxShowsPerWeek,
            priority = priority,
            scheduledAtMillis = scheduledAtMillis,
            targetAudience = targetAudience,
            inactiveDaysThreshold = inactiveDaysThreshold,
            isUrgent = isUrgent
        )
    }

    fun parseFromFcmData(
        messageId: String?,
        notificationTitle: String?,
        notificationBody: String?,
        data: Map<String, String>
    ): AnnouncementItem? {
        val id = (
            data["announcementId"]
                ?: data["id"]
                ?: data["announcement_id"]
                ?: messageId
            )?.trim().orEmpty()
        val title = (notificationTitle ?: data["title"] ?: data["heading"])?.trim().orEmpty()
        val body = (notificationBody ?: data["body"] ?: data["message"] ?: data["description"])?.trim().orEmpty()
        if (id.isEmpty() && title.isEmpty() && body.isEmpty()) return null
        val effectiveId = id.ifEmpty { "push_${System.currentTimeMillis()}_${("$title:$body").hashCode()}" }

        val mergedMap = mutableMapOf<String, Any?>()
        mergedMap.putAll(data)
        mergedMap["title"] = title
        mergedMap["body"] = body
        return parseAnnouncementDocument(
            id = effectiveId,
            data = mergedMap,
            allowInactiveOrExpired = true
        )
    }

    internal fun parseTimestampMillis(raw: Any?, defaultMillis: Long): Long {
        if (raw == null) return defaultMillis
        return when (raw) {
            is com.google.firebase.Timestamp -> raw.toDate().time
            is java.util.Date -> raw.time
            is Number -> {
                val value = raw.toLong()
                // Support both epoch seconds (10 digits) and epoch milliseconds (13 digits)
                if (value in 1_000_000_000L..9_999_999_999L) value * 1000L else value
            }
            is Map<*, *> -> {
                val secondsRaw = (raw["seconds"] as? Number)?.toLong()
                    ?: (raw["_seconds"] as? Number)?.toLong()
                if (secondsRaw != null) {
                    val nanosRaw = (raw["nanoseconds"] as? Number)?.toLong()
                        ?: (raw["_nanoseconds"] as? Number)?.toLong()
                        ?: 0L
                    secondsRaw * 1000L + (nanosRaw / 1_000_000L)
                } else {
                    defaultMillis
                }
            }
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return defaultMillis
                trimmed.toLongOrNull()?.let { numeric ->
                    return if (numeric in 1_000_000_000L..9_999_999_999L) numeric * 1000L else numeric
                }
                runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
                    ?: runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()
                    ?: runCatching {
                        LocalDateTime.parse(trimmed)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }.getOrNull()
                    ?: runCatching {
                        LocalDate.parse(trimmed)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }.getOrNull()
                    ?: defaultMillis
            }
            else -> defaultMillis
        }
    }

    private fun parsePositiveInt(raw: Any?, defaultVal: Int): Int {
        val parsed = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
        return if (parsed != null && parsed >= 1) parsed else defaultVal
    }

    private fun parseSignedInt(raw: Any?, defaultVal: Int): Int {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: defaultVal
            else -> defaultVal
        }
    }

    private fun parseBooleanFlag(raw: Any?, defaultVal: Boolean): Boolean {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase(Locale.US)) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> defaultVal
            }
            else -> defaultVal
        }
    }
}

/**
 * Persistent local store for scheduled announcements created on-device or received via FCM ahead
 * of their scheduledAt time, ensuring scheduled announcements survive app restarts and fire their
 * notification + Home popup right at scheduledAt.
 */
object AnnouncementScheduleStore {
    private const val PREFS_NAME = "one_crore_scheduled_announcements_prefs"
    private const val KEY_ITEMS = "scheduled_announcement_items"
    private const val KEY_DISPATCHED_PUSH_IDS = "dispatched_push_announcement_ids"

    fun saveAnnouncement(context: Context, item: AnnouncementItem) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getAllAnnouncements(context).filterNot { it.id == item.id }
        val updated = existing + item
        val arr = JSONArray()
        updated.forEach { arr.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun getAllAnnouncements(context: Context): List<AnnouncementItem> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    AnnouncementItem.fromJsonObject(obj)?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAnnouncementById(context: Context, announcementId: String): AnnouncementItem? {
        if (announcementId.isBlank()) return null
        return getAllAnnouncements(context).firstOrNull { it.id == announcementId }
    }

    fun getActiveAnnouncementsAt(context: Context, nowMillis: Long): List<AnnouncementItem> {
        return getAllAnnouncements(context).filter { item ->
            nowMillis in item.effectiveStartAtMillis..item.endAtMillis
        }
    }

    fun getPendingFutureAnnouncements(context: Context, nowMillis: Long): List<AnnouncementItem> {
        return getAllAnnouncements(context).filter { item ->
            item.effectiveStartAtMillis > nowMillis && item.endAtMillis >= item.effectiveStartAtMillis
        }
    }

    fun wasPushDispatched(context: Context, announcementId: String): Boolean {
        if (announcementId.isBlank()) return true
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_DISPATCHED_PUSH_IDS, emptySet()) ?: emptySet()
        return announcementId in set
    }

    fun markPushDispatched(context: Context, announcementId: String) {
        if (announcementId.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_DISPATCHED_PUSH_IDS, emptySet()) ?: emptySet()).toMutableSet()
        current.add(announcementId)
        prefs.edit().putStringSet(KEY_DISPATCHED_PUSH_IDS, current).apply()
    }

    fun removeAnnouncement(context: Context, announcementId: String) {
        if (announcementId.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getAllAnnouncements(context).filterNot { it.id == announcementId }
        val arr = JSONArray()
        existing.forEach { arr.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun resetForTest(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}

/**
 * Enforces app-open-only announcement frequency limits per user across ALL announcements
 * as well as per-announcement history, scheduled time windows, and audience targeting:
 * - Only inside [effectiveStartAtMillis, endAtMillis] window (never before scheduledAtMillis).
 * - Matches targetAudience (ALL, INCOMPLETE_DAILY_TARGET, or INACTIVE_DAYS with configurable X days).
 * - Minimum 10-minute gap between any two announcement shows.
 * - Configurable daily cap (3..5, default 3) per local calendar day.
 * - Rolling 7-day weekly cap of 21 shows.
 * - Dismissing makes the announcement eligible again after 10 minutes until caps or Join tap.
 * - Tapping the Join/action button marks acted-on so that announcement never shows again.
 */
object AnnouncementFrequencyStore {
    private const val PREFS_NAME = "one_crore_announcements_prefs"
    const val SEVEN_DAYS_MILLIS = 7L * 24L * 60L * 60L * 1000L
    const val MIN_SHOW_INTERVAL_MILLIS = AnnouncementItem.MIN_SHOW_INTERVAL_MILLIS
    const val MIN_CONFIGURABLE_DAILY_CAP = AnnouncementItem.MIN_CONFIGURABLE_DAILY_CAP
    const val MAX_CONFIGURABLE_DAILY_CAP = AnnouncementItem.MAX_CONFIGURABLE_DAILY_CAP
    const val DEFAULT_DAILY_CAP = AnnouncementItem.DEFAULT_MAX_SHOWS_PER_DAY
    const val DEFAULT_WEEKLY_CAP = AnnouncementItem.DEFAULT_MAX_SHOWS_PER_WEEK
    private const val SESSION_GAP_FOR_PRIOR_ACTIVITY_MILLIS = 30L * 60L * 1000L

    @Volatile
    var configuredMaxShowsPerDay: Int = DEFAULT_DAILY_CAP
        set(value) {
            field = value.coerceIn(MIN_CONFIGURABLE_DAILY_CAP, MAX_CONFIGURABLE_DAILY_CAP)
        }

    private fun stateKey(userId: String, announcementId: String): String {
        val safeUser = userId.trim().ifBlank { "default" }
        return "ann_state_${safeUser}_$announcementId"
    }

    private fun globalStateKey(userId: String): String {
        val safeUser = userId.trim().ifBlank { "default" }
        return "ann_global_$safeUser"
    }

    private fun audienceContextKey(userId: String): String {
        val safeUser = userId.trim().ifBlank { "default" }
        return "ann_audience_ctx_$safeUser"
    }

    private fun activityKey(userId: String): String {
        val safeUser = userId.trim().ifBlank { "default" }
        return "ann_activity_$safeUser"
    }

    private fun pushGlobalStateKey(userId: String): String {
        val safeUser = userId.trim().ifBlank { "default" }
        return "ann_push_global_$safeUser"
    }

    fun isAudienceEligible(
        announcement: AnnouncementItem,
        audienceContext: AnnouncementAudienceContext?,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        return when (announcement.targetAudience) {
            AnnouncementAudience.ALL -> true
            AnnouncementAudience.INCOMPLETE_DAILY_TARGET -> {
                if (audienceContext == null) return true
                val target = audienceContext.dailyTarget.coerceAtLeast(1).toLong()
                audienceContext.todayCount < target
            }
            AnnouncementAudience.INACTIVE_DAYS -> {
                if (audienceContext == null) return true
                if (audienceContext.todayCount > 0L) {
                    return false
                }
                val requiredDays = announcement.inactiveDaysThreshold.coerceAtLeast(1)
                val requiredMillis = requiredDays.toLong() * AnnouncementItem.ONE_DAY_MILLIS
                when {
                    audienceContext.lastActiveAtMillis > 0L -> {
                        (nowMillis - audienceContext.lastActiveAtMillis) >= requiredMillis
                    }
                    !audienceContext.latestActiveDateKey.isNullOrBlank() -> {
                        val lastDate = runCatching {
                            LocalDate.parse(audienceContext.latestActiveDateKey.trim())
                        }.getOrNull() ?: return false
                        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
                        ChronoUnit.DAYS.between(lastDate, today) >= requiredDays
                    }
                    else -> false
                }
            }
        }
    }

    fun cacheAudienceContext(
        context: Context,
        userId: String,
        audienceContext: AnnouncementAudienceContext
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject().apply {
            put("todayCount", audienceContext.todayCount)
            put("dailyTarget", audienceContext.dailyTarget)
            put("lastActiveAtMillis", audienceContext.lastActiveAtMillis)
            if (!audienceContext.latestActiveDateKey.isNullOrBlank()) {
                put("latestActiveDateKey", audienceContext.latestActiveDateKey)
            }
        }
        prefs.edit().putString(audienceContextKey(userId), obj.toString()).apply()
    }

    fun readCachedAudienceContext(
        context: Context,
        userId: String
    ): AnnouncementAudienceContext? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(audienceContextKey(userId), null) ?: return null
        return try {
            val obj = JSONObject(raw)
            AnnouncementAudienceContext(
                todayCount = obj.optLong("todayCount", 0L),
                dailyTarget = obj.optInt("dailyTarget", 2600),
                lastActiveAtMillis = obj.optLong("lastActiveAtMillis", 0L),
                latestActiveDateKey = obj.optString("latestActiveDateKey", "").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Records an app open for the user while preserving the prior session's lastActive timestamp
     * so that a user opening the app after >= X days of inactivity still matches INACTIVE_DAYS
     * announcements during that session before reverting to active.
     */
    fun recordUserAppOpen(
        context: Context,
        userId: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(activityKey(userId), null)
        var previousLastActive = 0L
        var currentLastActive = 0L
        if (raw != null) {
            runCatching {
                val obj = JSONObject(raw)
                previousLastActive = obj.optLong("priorSessionActiveMillis", 0L)
                currentLastActive = obj.optLong("lastActiveMillis", 0L)
            }
        }
        val effectivePrior = if (currentLastActive > 0L &&
            (nowMillis - currentLastActive) >= SESSION_GAP_FOR_PRIOR_ACTIVITY_MILLIS
        ) {
            currentLastActive
        } else {
            previousLastActive
        }
        val out = JSONObject().apply {
            put("priorSessionActiveMillis", effectivePrior)
            put("lastActiveMillis", nowMillis)
        }
        prefs.edit().putString(activityKey(userId), out.toString()).apply()
    }

    fun setLastActiveForTest(
        context: Context,
        userId: String,
        lastActiveMillis: Long
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val out = JSONObject().apply {
            put("priorSessionActiveMillis", lastActiveMillis)
            put("lastActiveMillis", lastActiveMillis)
        }
        prefs.edit().putString(activityKey(userId), out.toString()).apply()
    }

    fun getEffectiveLastActiveAtMillis(
        context: Context,
        userId: String,
        isAppInForegroundSession: Boolean = false
    ): Long {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(activityKey(userId), null) ?: return 0L
        return try {
            val obj = JSONObject(raw)
            val prior = obj.optLong("priorSessionActiveMillis", 0L)
            val latest = obj.optLong("lastActiveMillis", 0L)
            if (isAppInForegroundSession && prior > 0L) prior else latest
        } catch (_: Exception) {
            0L
        }
    }

    fun isEligibleToShow(
        context: Context,
        userId: String,
        announcement: AnnouncementItem,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        globalDailyCap: Int = configuredMaxShowsPerDay,
        globalWeeklyCap: Int = DEFAULT_WEEKLY_CAP,
        minIntervalMillis: Long = MIN_SHOW_INTERVAL_MILLIS,
        audienceContext: AnnouncementAudienceContext? = null
    ): Boolean {
        if (nowMillis < announcement.effectiveStartAtMillis || nowMillis > announcement.endAtMillis) {
            return false
        }
        if (MilestoneAnnouncementManager.isMilestoneCompletedForUser(context, userId, announcement.id)) {
            return false
        }

        val resolvedAudienceContext = audienceContext ?: readCachedAudienceContext(context, userId)
        if (!isAudienceEligible(announcement, resolvedAudienceContext, nowMillis, zoneId)) {
            return false
        }

        val record = readRecord(context, userId, announcement.id)
        if (record.actedOn) {
            return false
        }

        // Urgent owner announcements bypass all 10-min, 3/day, and 21/week caps while still
        // respecting schedule windows, audience targeting, and once-dismissed/acted suppression.
        if (announcement.isUrgent) {
            return true
        }

        val weekStartMillis = nowMillis - SEVEN_DAYS_MILLIS
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val effectiveGlobalDailyCap = globalDailyCap.coerceIn(
            MIN_CONFIGURABLE_DAILY_CAP,
            MAX_CONFIGURABLE_DAILY_CAP
        )

        // 1. Cross-announcement (global) cooldown & daily/weekly ceilings
        val globalRecord = readGlobalRecord(context, userId)
        val globalLastShown = globalRecord.lastShownAtMillis
        if (globalLastShown > 0L && (nowMillis - globalLastShown) in 0L until minIntervalMillis) {
            return false
        }
        val globalImpressionsInLastWeek = globalRecord.impressionTimestamps.count { it >= weekStartMillis }
        if (globalImpressionsInLastWeek >= globalWeeklyCap) {
            return false
        }
        val globalImpressionsToday = globalRecord.impressionTimestamps.count { ts ->
            Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate() == today
        }
        if (globalImpressionsToday >= effectiveGlobalDailyCap) {
            return false
        }

        // 2. Per-announcement cooldown & daily/weekly ceilings
        val docLastShown = record.lastShownAtMillis
        if (docLastShown > 0L && (nowMillis - docLastShown) in 0L until minIntervalMillis) {
            return false
        }

        val docDailyCap = announcement.maxShowsPerDay.coerceAtLeast(1)
        val docWeeklyCap = announcement.maxShowsPerWeek.coerceAtLeast(1)

        val docImpressionsInLastWeek = record.impressionTimestamps.count { it >= weekStartMillis }
        if (docImpressionsInLastWeek >= docWeeklyCap) {
            return false
        }

        val docImpressionsToday = record.impressionTimestamps.count { ts ->
            Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate() == today
        }
        if (docImpressionsToday >= docDailyCap) {
            return false
        }

        return true
    }

    /**
     * Evaluates whether an incoming or scheduled push notification is eligible to post for this
     * user right now (enforcing scheduledAt, audience targeting, 10-min gap, 3/day cap, 21/week cap,
     * unless isUrgent is true).
     */
    fun isEligibleForPush(
        context: Context,
        userId: String,
        announcement: AnnouncementItem,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        audienceContext: AnnouncementAudienceContext? = null
    ): Boolean {
        if (!isEligibleToShow(
                context = context,
                userId = userId,
                announcement = announcement,
                nowMillis = nowMillis,
                zoneId = zoneId,
                audienceContext = audienceContext
            )
        ) {
            return false
        }
        if (announcement.isUrgent) {
            return true
        }
        val pushRecord = readPushGlobalRecord(context, userId)
        if (pushRecord.lastShownAtMillis > 0L &&
            (nowMillis - pushRecord.lastShownAtMillis) in 0L until MIN_SHOW_INTERVAL_MILLIS
        ) {
            return false
        }
        val weekStartMillis = nowMillis - SEVEN_DAYS_MILLIS
        if (pushRecord.impressionTimestamps.count { it >= weekStartMillis } >= DEFAULT_WEEKLY_CAP) {
            return false
        }
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val effectiveDailyCap = configuredMaxShowsPerDay.coerceIn(
            MIN_CONFIGURABLE_DAILY_CAP,
            MAX_CONFIGURABLE_DAILY_CAP
        )
        val pushesToday = pushRecord.impressionTimestamps.count { ts ->
            Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate() == today
        }
        return pushesToday < effectiveDailyCap
    }

    fun recordPushSent(
        context: Context,
        userId: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val cutoffMillis = nowMillis - SEVEN_DAYS_MILLIS
        val current = readPushGlobalRecord(context, userId)
        val updated = (current.impressionTimestamps + nowMillis).filter { it >= cutoffMillis }
        writePushGlobalRecord(
            context = context,
            userId = userId,
            record = current.copy(
                impressionTimestamps = updated,
                lastShownAtMillis = maxOf(current.lastShownAtMillis, nowMillis)
            )
        )
    }

    fun selectNextEligibleAnnouncement(
        context: Context,
        userId: String,
        candidates: List<AnnouncementItem>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        globalDailyCap: Int = configuredMaxShowsPerDay,
        audienceContext: AnnouncementAudienceContext? = null
    ): AnnouncementItem? {
        return candidates
            .sortedWith(
                compareByDescending<AnnouncementItem> { it.isUrgent }
                    .thenByDescending { it.priority }
                    .thenByDescending { it.effectiveStartAtMillis }
                    .thenBy { it.id }
            )
            .firstOrNull { candidate ->
                isEligibleToShow(
                    context = context,
                    userId = userId,
                    announcement = candidate,
                    nowMillis = nowMillis,
                    zoneId = zoneId,
                    globalDailyCap = globalDailyCap,
                    audienceContext = audienceContext
                )
            }
    }

    fun recordShown(
        context: Context,
        userId: String,
        announcement: AnnouncementItem,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val cutoffMillis = nowMillis - SEVEN_DAYS_MILLIS

        val currentDoc = readRecord(context, userId, announcement.id)
        val updatedDocTimestamps = (currentDoc.impressionTimestamps + nowMillis)
            .filter { it >= cutoffMillis }
        writeRecord(
            context = context,
            userId = userId,
            announcementId = announcement.id,
            record = currentDoc.copy(
                impressionTimestamps = updatedDocTimestamps,
                lastShownAtMillis = maxOf(currentDoc.lastShownAtMillis, nowMillis)
            )
        )

        if (announcement.isUrgent) {
            return
        }

        val currentGlobal = readGlobalRecord(context, userId)
        val updatedGlobalTimestamps = (currentGlobal.impressionTimestamps + nowMillis)
            .filter { it >= cutoffMillis }
        writeGlobalRecord(
            context = context,
            userId = userId,
            record = currentGlobal.copy(
                impressionTimestamps = updatedGlobalTimestamps,
                lastShownAtMillis = maxOf(currentGlobal.lastShownAtMillis, nowMillis)
            )
        )
    }

    fun markActedOn(
        context: Context,
        userId: String,
        announcementId: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (announcementId.isBlank()) return
        val current = readRecord(context, userId, announcementId)
        val updatedTimestamps = if (current.impressionTimestamps.isEmpty()) {
            listOf(nowMillis)
        } else {
            current.impressionTimestamps
        }
        writeRecord(
            context = context,
            userId = userId,
            announcementId = announcementId,
            record = current.copy(
                impressionTimestamps = updatedTimestamps,
                actedOn = true,
                lastShownAtMillis = maxOf(current.lastShownAtMillis, nowMillis)
            )
        )
    }

    fun hasActedOn(context: Context, userId: String, announcementId: String): Boolean {
        return readRecord(context, userId, announcementId).actedOn
    }

    fun getLastGlobalShownAtMillis(context: Context, userId: String): Long {
        return readGlobalRecord(context, userId).lastShownAtMillis
    }

    fun getAnnouncementShownTimestamps(
        context: Context,
        userId: String,
        announcementId: String
    ): List<Long> {
        return readRecord(context, userId, announcementId).impressionTimestamps
    }

    fun resetForTest(context: Context) {
        configuredMaxShowsPerDay = DEFAULT_DAILY_CAP
        AnnouncementScheduleStore.resetForTest(context)
        AnnouncementOwnerConfig.resetForTest(context)
        MilestoneAnnouncementManager.resetForTest(context)
        AnnouncementHistoryStore.resetForTest(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private data class StoredRecord(
        val impressionTimestamps: List<Long> = emptyList(),
        val actedOn: Boolean = false,
        val lastShownAtMillis: Long = 0L
    )

    private data class GlobalStoredRecord(
        val impressionTimestamps: List<Long> = emptyList(),
        val lastShownAtMillis: Long = 0L
    )

    private fun readRecord(context: Context, userId: String, announcementId: String): StoredRecord {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(stateKey(userId, announcementId), null) ?: return StoredRecord()
        return try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("timestamps") ?: JSONArray()
            val timestamps = buildList {
                for (i in 0 until arr.length()) {
                    val ts = arr.optLong(i, -1L)
                    if (ts > 0L) add(ts)
                }
            }
            val savedLastShown = obj.optLong("lastShownAtMillis", 0L)
            val effectiveLastShown = maxOf(savedLastShown, timestamps.maxOrNull() ?: 0L)
            StoredRecord(
                impressionTimestamps = timestamps,
                actedOn = obj.optBoolean("actedOn", false),
                lastShownAtMillis = effectiveLastShown
            )
        } catch (_: Exception) {
            StoredRecord()
        }
    }

    private fun writeRecord(
        context: Context,
        userId: String,
        announcementId: String,
        record: StoredRecord
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        record.impressionTimestamps.forEach { arr.put(it) }
        val obj = JSONObject().apply {
            put("timestamps", arr)
            put("actedOn", record.actedOn)
            put("lastShownAtMillis", record.lastShownAtMillis)
        }
        prefs.edit()
            .putString(stateKey(userId, announcementId), obj.toString())
            .apply()
    }

    private fun readGlobalRecord(context: Context, userId: String): GlobalStoredRecord {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(globalStateKey(userId), null) ?: return GlobalStoredRecord()
        return try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("timestamps") ?: JSONArray()
            val timestamps = buildList {
                for (i in 0 until arr.length()) {
                    val ts = arr.optLong(i, -1L)
                    if (ts > 0L) add(ts)
                }
            }
            val savedLastShown = obj.optLong("lastShownAtMillis", 0L)
            val effectiveLastShown = maxOf(savedLastShown, timestamps.maxOrNull() ?: 0L)
            GlobalStoredRecord(
                impressionTimestamps = timestamps,
                lastShownAtMillis = effectiveLastShown
            )
        } catch (_: Exception) {
            GlobalStoredRecord()
        }
    }

    private fun writeGlobalRecord(
        context: Context,
        userId: String,
        record: GlobalStoredRecord
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        record.impressionTimestamps.forEach { arr.put(it) }
        val obj = JSONObject().apply {
            put("timestamps", arr)
            put("lastShownAtMillis", record.lastShownAtMillis)
        }
        prefs.edit()
            .putString(globalStateKey(userId), obj.toString())
            .apply()
    }

    private fun readPushGlobalRecord(context: Context, userId: String): GlobalStoredRecord {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(pushGlobalStateKey(userId), null) ?: return GlobalStoredRecord()
        return try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("timestamps") ?: JSONArray()
            val timestamps = buildList {
                for (i in 0 until arr.length()) {
                    val ts = arr.optLong(i, -1L)
                    if (ts > 0L) add(ts)
                }
            }
            val savedLastShown = obj.optLong("lastShownAtMillis", 0L)
            val effectiveLastShown = maxOf(savedLastShown, timestamps.maxOrNull() ?: 0L)
            GlobalStoredRecord(
                impressionTimestamps = timestamps,
                lastShownAtMillis = effectiveLastShown
            )
        } catch (_: Exception) {
            GlobalStoredRecord()
        }
    }

    private fun writePushGlobalRecord(
        context: Context,
        userId: String,
        record: GlobalStoredRecord
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        record.impressionTimestamps.forEach { arr.put(it) }
        val obj = JSONObject().apply {
            put("timestamps", arr)
            put("lastShownAtMillis", record.lastShownAtMillis)
        }
        prefs.edit()
            .putString(pushGlobalStateKey(userId), obj.toString())
            .apply()
    }
}
