package com.example.data

import android.content.Context
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GlobalCounterStats(
    val totalCount: Long,
    val userCount: Long,
    val fetchedAtMillis: Long,
    val sentMilestones: List<Long> = emptyList()
)

object GlobalStatsCache {
    const val ONE_HOUR_MILLIS = 60L * 60L * 1000L
    private const val PREFS_NAME = "one_crore_global_stats_cache"
    private const val KEY_HAS_VALUE = "has_value"
    private const val KEY_TOTAL_COUNT = "total_count"
    private const val KEY_USER_COUNT = "user_count"
    private const val KEY_FETCHED_AT_MILLIS = "fetched_at_millis"
    private const val KEY_SENT_MILESTONES = "sent_milestones"

    fun shouldFetchFromServer(
        cached: GlobalCounterStats?,
        nowMillis: Long,
        isOnline: Boolean
    ): Boolean {
        if (!isOnline) return false
        if (cached == null) return true
        if (cached.fetchedAtMillis <= 0L) return true
        val ageMillis = nowMillis - cached.fetchedAtMillis
        return ageMillis < 0L || ageMillis >= ONE_HOUR_MILLIS
    }

    private fun parseNonNegativeLong(raw: Any?): Long? {
        val value = when (raw) {
            is Number -> raw.toLong()
            is String -> {
                val cleaned = raw.trim().replace(",", "")
                cleaned.toLongOrNull() ?: cleaned.toDoubleOrNull()?.toLong()
            }
            else -> null
        }
        return value?.takeIf { it >= 0L }
    }

    internal fun parseMilestoneList(raw: Any?): List<Long> {
        val items: Collection<*> = when (raw) {
            is Iterable<*> -> raw.toList()
            is Array<*> -> raw.toList()
            is Map<*, *> -> raw.entries.filter { (_, v) -> v != false && v != null }.map { it.key }
            else -> emptyList<Any?>()
        }
        return items.mapNotNull { entry ->
            when (entry) {
                is Number -> entry.toLong().takeIf { it > 0L }
                is String -> {
                    val trimmed = entry.trim()
                    val digits = trimmed.removePrefix("milestone_").replace(",", "")
                    digits.toLongOrNull()?.takeIf { it > 0L }
                }
                else -> null
            }
        }.distinct().sorted()
    }

    fun parseGlobalStatsDocument(
        exists: Boolean,
        data: Map<String, Any?>?,
        fetchedAtMillis: Long
    ): GlobalCounterStats? {
        if (!exists || data == null) return null
        val totalCount = parseNonNegativeLong(data["totalCount"]) ?: return null
        val userCount = parseNonNegativeLong(data["userCount"]) ?: return null
        val sentMilestones = parseMilestoneList(
            data["sentMilestones"] ?: data["milestonesSent"] ?: data["milestones"]
        )
        return GlobalCounterStats(
            totalCount = totalCount,
            userCount = userCount,
            fetchedAtMillis = fetchedAtMillis.coerceAtLeast(1L),
            sentMilestones = sentMilestones
        )
    }

    fun formatTotalLine(totalCount: Long): String {
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(totalCount)
        return "Everyone together: $formatted Durood"
    }

    fun formatUsersLine(userCount: Long): String {
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(userCount)
        return "$formatted users"
    }

    fun formatSubtitleLine(
        isOnline: Boolean,
        fetchedAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (isOnline || fetchedAtMillis <= 0L) {
            return "Updates every hour"
        }
        return "Updates every hour · Updated ${formatLastUpdatedTime(fetchedAtMillis, nowMillis)}"
    }

    fun formatLastUpdatedTime(
        fetchedAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (fetchedAtMillis <= 0L) return "recently"
        val diff = (nowMillis - fetchedAtMillis).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "just now"
            diff < 3_600_000L -> "${diff / 60_000L}m ago"
            diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
            else -> SimpleDateFormat("dd MMM, h:mm a", Locale.US).format(Date(fetchedAtMillis))
        }
    }

    fun getCached(context: Context): GlobalCounterStats? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_VALUE, false)) return null
        val total = prefs.getLong(KEY_TOTAL_COUNT, 0L)
        val users = prefs.getLong(KEY_USER_COUNT, 0L)
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT_MILLIS, 0L)
        if (total < 0L || users < 0L || fetchedAt <= 0L) return null
        val sentMilestones = prefs.getString(KEY_SENT_MILESTONES, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull()?.takeIf { v -> v > 0L } }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
        return GlobalCounterStats(
            totalCount = total,
            userCount = users,
            fetchedAtMillis = fetchedAt,
            sentMilestones = sentMilestones
        )
    }

    fun saveCached(context: Context, stats: GlobalCounterStats) {
        if (stats.totalCount < 0L || stats.userCount < 0L) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_VALUE, true)
            .putLong(KEY_TOTAL_COUNT, stats.totalCount)
            .putLong(KEY_USER_COUNT, stats.userCount)
            .putLong(KEY_FETCHED_AT_MILLIS, stats.fetchedAtMillis)
            .putString(KEY_SENT_MILESTONES, stats.sentMilestones.distinct().sorted().joinToString(","))
            .apply()
    }

    fun clearCache(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
