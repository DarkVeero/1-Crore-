package com.example.announcements

import android.content.Context
import com.example.data.GlobalCounterStats

object MilestoneAnnouncementManager {
    const val MILESTONE_ONE_LAKH = 100_000L
    const val MILESTONE_TEN_LAKH = 1_000_000L
    const val MILESTONE_ONE_CRORE = 10_000_000L
    val THRESHOLDS = listOf(MILESTONE_ONE_LAKH, MILESTONE_TEN_LAKH, MILESTONE_ONE_CRORE)

    private const val PREFS_NAME = "one_crore_milestone_announcements_prefs"
    private const val MILESTONE_ID_PREFIX = "milestone_"

    fun milestoneAnnouncementId(threshold: Long): String = "$MILESTONE_ID_PREFIX$threshold"

    fun isMilestoneAnnouncementId(announcementId: String): Boolean {
        return announcementId.startsWith(MILESTONE_ID_PREFIX)
    }

    fun parseMilestoneThresholdFromId(announcementId: String): Long? {
        if (!isMilestoneAnnouncementId(announcementId)) return null
        return announcementId.removePrefix(MILESTONE_ID_PREFIX).toLongOrNull()
    }

    fun crossedMilestones(totalCount: Long): List<Long> {
        if (totalCount <= 0L) return emptyList()
        return THRESHOLDS.filter { totalCount >= it }
    }

    fun buildMilestoneAnnouncement(
        threshold: Long,
        nowMillis: Long = System.currentTimeMillis(),
        linkUrl: String? = null,
        buttonText: String? = null
    ): AnnouncementItem {
        val cleanUrl = linkUrl?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }
        val effectiveButtonText = if (cleanUrl != null) {
            buttonText?.trim()?.ifBlank { "Join" } ?: "Join"
        } else {
            null
        }
        val (title, body) = when (threshold) {
            MILESTONE_ONE_LAKH -> {
                "1 Lakh Durood Milestone! 🌙" to
                    "الْحَمْدُ لِلَّهِ! Everyone together has crossed 100,000 Durood Pak. Keep reciting to reach 1 Crore!"
            }

            MILESTONE_TEN_LAKH -> {
                "10 Lakh Durood Milestone! ✨" to
                    "مَا شَاءَ اللَّهُ! Our community together has crossed 1,000,000 Durood Pak toward our 1 Crore goal!"
            }

            else -> {
                "$threshold Durood Milestone!" to
                    "الْحَمْدُ لِلَّهِ! Our community has reached $threshold Durood Pak together."
            }
        }
        val priority = if (threshold >= MILESTONE_TEN_LAKH) 21 else 20
        return AnnouncementItem(
            id = milestoneAnnouncementId(threshold),
            title = title,
            body = body,
            buttonText = effectiveButtonText,
            buttonAction = if (cleanUrl != null) AnnouncementAction.LINK else AnnouncementAction.NONE,
            buttonUrl = cleanUrl,
            startAtMillis = nowMillis,
            endAtMillis = nowMillis + 30L * AnnouncementItem.ONE_DAY_MILLIS,
            maxShowsPerDay = 1,
            maxShowsPerWeek = 1,
            priority = priority,
            targetAudience = AnnouncementAudience.ALL
        )
    }

    private fun completedKey(userId: String): String {
        val safeUser = userId.trim().ifBlank { "default" }
        return "completed_milestones_$safeUser"
    }

    fun getCompletedMilestoneIds(context: Context, userId: String): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(completedKey(userId), emptySet()) ?: emptySet()
    }

    fun isMilestoneCompletedForUser(
        context: Context,
        userId: String,
        announcementId: String
    ): Boolean {
        if (!isMilestoneAnnouncementId(announcementId)) return false
        return announcementId in getCompletedMilestoneIds(context, userId)
    }

    fun markMilestoneCompletedForUser(
        context: Context,
        userId: String,
        announcementId: String
    ) {
        if (!isMilestoneAnnouncementId(announcementId)) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(completedKey(userId), emptySet()) ?: emptySet()).toMutableSet()
        if (current.add(announcementId)) {
            prefs.edit().putStringSet(completedKey(userId), current).apply()
        }
    }

    fun getEligibleMilestoneCandidates(
        context: Context,
        userId: String,
        globalStats: GlobalCounterStats?,
        nowMillis: Long = System.currentTimeMillis()
    ): List<AnnouncementItem> {
        if (globalStats == null) return emptyList()
        val completed = getCompletedMilestoneIds(context, userId)
        return crossedMilestones(globalStats.totalCount)
            .map { threshold -> buildMilestoneAnnouncement(threshold = threshold, nowMillis = nowMillis) }
            .filterNot { item -> item.id in completed }
    }

    fun resetForTest(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
