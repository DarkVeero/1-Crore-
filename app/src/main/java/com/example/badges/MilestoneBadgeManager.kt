package com.example.badges

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.NumberFormat
import java.util.Locale

data class MilestoneBadge(
    val tier: Int,
    val threshold: Long,
    val shortLabel: String,
    val title: String,
    val subtitle: String
)

data class MilestoneBadgeProgress(
    val currentTotalCount: Long,
    val earnedCount: Int,
    val totalBadges: Int = MilestoneBadgeManager.TOTAL_BADGES,
    val celebratedTier: Int = 0,
    val latestEarnedBadge: MilestoneBadge? = null,
    val nextBadge: MilestoneBadge? = null,
    val remainingToNext: Long = MilestoneBadgeManager.BADGE_STEP,
    val progressToNextFraction: Float = 0f,
    val earnedTimestamps: Map<Int, Long> = emptyMap()
)

object MilestoneBadgeManager {
    const val BADGE_STEP = 50_000L
    const val TOTAL_BADGES = 200
    const val ONE_CRORE = 10_000_000L

    private const val PREFS_NAME = "one_crore_milestone_badges_prefs"

    private val _badgeUpdates = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val badgeUpdates: SharedFlow<String> = _badgeUpdates.asSharedFlow()

    val ALL_BADGES: List<MilestoneBadge> by lazy {
        (1..TOTAL_BADGES).map { tier ->
            val threshold = tier.toLong() * BADGE_STEP
            val label = formatBadgeLabel(threshold)
            MilestoneBadge(
                tier = tier,
                threshold = threshold,
                shortLabel = label,
                title = "$label Badge",
                subtitle = "${NumberFormat.getNumberInstance(Locale.US).format(threshold)} Durood Pak"
            )
        }
    }

    fun formatBadgeLabel(threshold: Long): String {
        return when {
            threshold >= ONE_CRORE -> "1 Crore"
            threshold >= 100_000L -> {
                val wholeLakhs = threshold / 100_000L
                val remainder = threshold % 100_000L
                if (remainder == 0L) "$wholeLakhs Lakh" else "$wholeLakhs.5 Lakh"
            }
            else -> "50K"
        }
    }

    fun formatCompactChipLabel(threshold: Long): String {
        return when {
            threshold >= ONE_CRORE -> "1Cr"
            threshold >= 100_000L -> {
                val wholeLakhs = threshold / 100_000L
                val remainder = threshold % 100_000L
                if (remainder == 0L) "${wholeLakhs}L" else "${wholeLakhs}.5L"
            }
            else -> "50K"
        }
    }

    fun tierForCount(mainDuroodTotalCount: Long): Int {
        if (mainDuroodTotalCount < BADGE_STEP) return 0
        return (mainDuroodTotalCount / BADGE_STEP).toInt().coerceIn(0, TOTAL_BADGES)
    }

    fun badgeForTier(tier: Int): MilestoneBadge? {
        if (tier !in 1..TOTAL_BADGES) return null
        return ALL_BADGES[tier - 1]
    }

    fun evaluatePureProgress(
        mainDuroodTotalCount: Long,
        persistedHighestEarnedTier: Int = 0,
        persistedCelebratedTier: Int = 0,
        earnedTimestamps: Map<Int, Long> = emptyMap()
    ): MilestoneBadgeProgress {
        val safeTotal = mainDuroodTotalCount.coerceAtLeast(0L)
        val liveTier = tierForCount(safeTotal)
        val latestBadge = badgeForTier(liveTier)
        val nextBadge = badgeForTier(liveTier + 1)
        val prevThreshold = liveTier.toLong() * BADGE_STEP
        val remaining = if (nextBadge == null) {
            0L
        } else {
            (nextBadge.threshold - safeTotal).coerceAtLeast(0L)
        }
        val fraction = if (nextBadge == null) {
            1f
        } else {
            val inBand = (safeTotal - prevThreshold).coerceIn(0L, BADGE_STEP)
            (inBand.toDouble() / BADGE_STEP.toDouble()).toFloat().coerceIn(0f, 1f)
        }
        return MilestoneBadgeProgress(
            currentTotalCount = safeTotal,
            earnedCount = liveTier,
            totalBadges = TOTAL_BADGES,
            celebratedTier = persistedCelebratedTier.coerceIn(0, TOTAL_BADGES),
            latestEarnedBadge = latestBadge,
            nextBadge = nextBadge,
            remainingToNext = remaining,
            progressToNextFraction = fraction,
            earnedTimestamps = earnedTimestamps.filterKeys { it in 1..liveTier }
        )
    }

    private fun safeUserKey(userId: String): String = userId.trim().ifBlank { "default" }
    private fun earnedTierKey(userId: String): String = "earned_tier_${safeUserKey(userId)}"
    private fun celebratedTierKey(userId: String): String = "celebrated_tier_${safeUserKey(userId)}"
    private fun timestampsKey(userId: String): String = "timestamps_${safeUserKey(userId)}"

    private fun parseTimestamps(raw: String?): MutableMap<Int, Long> {
        val result = mutableMapOf<Int, Long>()
        if (raw.isNullOrBlank()) return result
        raw.split(';').forEach { entry ->
            val parts = entry.split(':')
            if (parts.size == 2) {
                val tier = parts[0].toIntOrNull()
                val millis = parts[1].toLongOrNull()
                if (tier != null && tier in 1..TOTAL_BADGES && millis != null && millis > 0L) {
                    result[tier] = millis
                }
            }
        }
        return result
    }

    private fun encodeTimestamps(map: Map<Int, Long>): String {
        return map.entries
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}:${it.value}" }
    }

    /**
     * Evaluates Main Durood lifetime progress directly from [mainDuroodTotalCount].
     * Badges unlock at each 50,000 threshold and lock again immediately if the total drops below.
     * Previously dismissed celebration tiers remain recorded so re-crossing a tier does not replay popups.
     */
    fun syncAndGetProgress(
        context: Context,
        userId: String,
        mainDuroodTotalCount: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): MilestoneBadgeProgress {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedEarned = prefs.getInt(earnedTierKey(userId), 0).coerceIn(0, TOTAL_BADGES)
        val storedCelebrated = prefs.getInt(celebratedTierKey(userId), 0).coerceIn(0, TOTAL_BADGES)
        val timestamps = parseTimestamps(prefs.getString(timestampsKey(userId), null))

        val liveTier = tierForCount(mainDuroodTotalCount)
        val removedLockedTimestamps = timestamps.keys.removeAll { it > liveTier }
        if (liveTier > storedEarned) {
            for (tier in (storedEarned + 1)..liveTier) {
                timestamps.putIfAbsent(tier, nowMillis)
            }
        }
        if (liveTier != storedEarned || removedLockedTimestamps) {
            prefs.edit()
                .putInt(earnedTierKey(userId), liveTier)
                .putString(timestampsKey(userId), encodeTimestamps(timestamps))
                .apply()
            _badgeUpdates.tryEmit(userId)
        }

        return evaluatePureProgress(
            mainDuroodTotalCount = mainDuroodTotalCount,
            persistedHighestEarnedTier = liveTier,
            persistedCelebratedTier = storedCelebrated,
            earnedTimestamps = timestamps.toMap()
        )
    }

    fun getPendingCelebrationBadge(
        context: Context,
        userId: String,
        mainDuroodTotalCount: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): MilestoneBadge? {
        val progress = syncAndGetProgress(
            context = context,
            userId = userId,
            mainDuroodTotalCount = mainDuroodTotalCount,
            nowMillis = nowMillis
        )
        return if (progress.earnedCount > progress.celebratedTier) {
            badgeForTier(progress.earnedCount)
        } else {
            null
        }
    }

    fun markBadgeCelebrated(
        context: Context,
        userId: String,
        tier: Int
    ) {
        val clamped = tier.coerceIn(0, TOTAL_BADGES)
        if (clamped <= 0) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCelebrated = prefs.getInt(celebratedTierKey(userId), 0).coerceIn(0, TOTAL_BADGES)
        val nextCelebrated = maxOf(currentCelebrated, clamped)
        if (nextCelebrated != currentCelebrated) {
            prefs.edit()
                .putInt(celebratedTierKey(userId), nextCelebrated)
                .apply()
            _badgeUpdates.tryEmit(userId)
        }
    }

    fun clearUser(context: Context, userId: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(earnedTierKey(userId))
            .remove(celebratedTierKey(userId))
            .remove(timestampsKey(userId))
            .apply()
        _badgeUpdates.tryEmit(userId)
    }

    fun resetForTest(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
