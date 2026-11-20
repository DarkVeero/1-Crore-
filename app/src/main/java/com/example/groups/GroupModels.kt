package com.example.groups

import com.example.data.DuroodRepository
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

enum class GroupType(val wireValue: String, val displayLabel: String) {
    MAIN_DUROOD("main_durood", "For Durood"),
    CUSTOM_ZIKR("custom_zikr", "Other Zikr");

    companion object {
        fun fromWire(raw: String?): GroupType {
            return when (raw?.trim()?.lowercase()) {
                "custom_zikr", "zikr" -> CUSTOM_ZIKR
                else -> MAIN_DUROOD
            }
        }
    }
}

enum class GroupCelebrationType(val wireValue: String) {
    WEEKLY_TOP_3_GROUP("weekly_top_3_group"),
    WEEKLY_TOP_3_PRIVATE("weekly_top_3_private"),
    TARGET_1_LAKH("target_1_lakh"),
    TARGET_1_CRORE("target_1_crore");

    companion object {
        fun fromWire(raw: String?): GroupCelebrationType {
            return entries.firstOrNull { it.wireValue == raw?.trim()?.lowercase() } ?: WEEKLY_TOP_3_GROUP
        }
    }
}

data class GroupMemberProgress(
    val userId: String,
    val displayName: String,
    val todayCount: Long,
    val todayDateKey: String,
    val lifetimeTotalCount: Long,
    val weeklyCount: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    fun effectiveTodayCount(currentDateKey: String = LocalDate.now().toString()): Long {
        return if (todayDateKey == currentDateKey) todayCount.coerceAtLeast(0L) else 0L
    }
}

data class GroupCelebrationNotice(
    val id: String,
    val groupId: String,
    val groupName: String,
    val type: GroupCelebrationType,
    val title: String,
    val message: String,
    val targetUserId: String? = null,
    val topMemberNames: List<String> = emptyList(),
    val createdAtMillis: Long = 0L
)

data class GroupItem(
    val id: String,
    val name: String,
    val inviteCode: String,
    val type: GroupType,
    val zikrName: String? = null,
    val targetCount: Long = DuroodRepository.ONE_CRORE,
    val creatorUserId: String,
    val members: List<GroupMemberProgress>,
    val serverTotalCount: Long? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val memberCount: Int
        get() = members.size

    val effectiveZikrName: String
        get() = if (type == GroupType.CUSTOM_ZIKR && !zikrName.isNullOrBlank()) {
            zikrName.trim()
        } else {
            "Durood"
        }

    val summedMemberTotal: Long
        get() = members.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) }

    val effectiveGroupTotal: Long
        get() = (serverTotalCount ?: summedMemberTotal).coerceAtLeast(0L)

    val progressFraction: Float
        get() = if (targetCount <= 0L) {
            0f
        } else {
            (effectiveGroupTotal.toDouble() / targetCount.toDouble()).toFloat().coerceIn(0f, 1f)
        }

    val progressPercentFormatted: String
        get() = String.format(Locale.US, "%.2f%%", progressFraction * 100f)

    fun rankedMembers(currentDateKey: String = LocalDate.now().toString()): List<GroupMemberProgress> {
        return members.sortedWith(
            compareByDescending<GroupMemberProgress> { it.lifetimeTotalCount.coerceAtLeast(0L) }
                .thenByDescending { it.effectiveTodayCount(currentDateKey) }
                .thenBy { it.displayName.lowercase(Locale.US) }
                .thenBy { it.userId }
        )
    }

    fun weeklyTopThree(): List<GroupMemberProgress> {
        return members
            .filter { it.weeklyCount > 0L }
            .sortedWith(
                compareByDescending<GroupMemberProgress> { it.weeklyCount }
                    .thenByDescending { it.lifetimeTotalCount }
                    .thenBy { it.displayName.lowercase(Locale.US) }
                    .thenBy { it.userId }
            )
            .take(3)
    }
}

object GroupLogic {
    const val MAX_GROUPS_PER_USER = 5
    const val GROUP_SYNC_INTERVAL_MILLIS = 5L * 60L * 1000L
    const val DEFAULT_UNIFIED_GROUP_TARGET = 100_000L
    const val TARGET_ONE_LAKH = 100_000L
    const val TARGET_ONE_CRORE = 10_000_000L
    private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun normalizeInviteCode(raw: String): String {
        return raw.trim()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")
    }

    fun extractInviteCodeInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val afterColon = Regex("""(?i)invite\s*code\s*[:\-]\s*([A-Za-z0-9\-\s]+)""")
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::normalizeInviteCode)
        if (!afterColon.isNullOrBlank()) {
            return afterColon
        }
        return normalizeInviteCode(trimmed)
    }

    fun generateInviteCode(seedRandom: java.security.SecureRandom = java.security.SecureRandom()): String {
        val sb = StringBuilder(6)
        repeat(6) {
            val idx = seedRandom.nextInt(INVITE_ALPHABET.length)
            sb.append(INVITE_ALPHABET[idx])
        }
        return sb.toString()
    }

    fun formatCount(value: Long): String = NumberFormat.getNumberInstance(Locale.US).format(value)

    fun currentIsoWeekKey(today: LocalDate = LocalDate.now()): String {
        val weekFields = WeekFields.ISO
        val week = today.get(weekFields.weekOfWeekBasedYear())
        val year = today.get(weekFields.weekBasedYear())
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }

    /**
     * Builds a link-free WhatsApp group invite containing ONLY the group name and invite code.
     */
    fun buildMergedWhatsAppInviteMessage(
        group: GroupItem,
        downloadUrl: String? = null
    ): String {
        val code = normalizeInviteCode(group.inviteCode)
        val cleanGroupName = group.name.trim().ifBlank { "1 Crore Group" }
        return buildString {
            appendLine(cleanGroupName)
            append("Invite Code: $code")
        }.trim()
    }

    /**
     * Computes both target celebrations (1 Lakh & 1 Crore group total) and
     * weekly Top-3 celebrations (group-wide announcement naming Top 3 + private celebration if current user is Top 3).
     */
    fun computeCelebrations(
        group: GroupItem,
        currentUserId: String,
        today: LocalDate = LocalDate.now(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<GroupCelebrationNotice> {
        val notices = mutableListOf<GroupCelebrationNotice>()
        val metricLabel = if (group.type == GroupType.CUSTOM_ZIKR && !group.zikrName.isNullOrBlank()) {
            group.zikrName
        } else {
            "Durood Pak"
        }
        val groupTotal = group.effectiveGroupTotal

        if (groupTotal >= TARGET_ONE_CRORE) {
            notices.add(
                GroupCelebrationNotice(
                    id = "${group.id}_target_$TARGET_ONE_CRORE",
                    groupId = group.id,
                    groupName = group.name,
                    type = GroupCelebrationType.TARGET_1_CRORE,
                    title = "1 Crore Group Milestone!",
                    message = "مَا شَاءَ اللَّهُ! ${group.name} has crossed 1,00,00,000 (1 Crore) $metricLabel together!",
                    createdAtMillis = nowMillis
                )
            )
        }
        if (groupTotal >= TARGET_ONE_LAKH) {
            notices.add(
                GroupCelebrationNotice(
                    id = "${group.id}_target_$TARGET_ONE_LAKH",
                    groupId = group.id,
                    groupName = group.name,
                    type = GroupCelebrationType.TARGET_1_LAKH,
                    title = "1 Lakh Group Milestone!",
                    message = "الْحَمْدُ لِلَّهِ! ${group.name} has crossed 1,00,000 (1 Lakh) $metricLabel together!",
                    createdAtMillis = nowMillis
                )
            )
        }

        val top3 = group.weeklyTopThree()
        if (top3.isNotEmpty()) {
            val weekKey = currentIsoWeekKey(today)
            val summary = top3.mapIndexed { index, m ->
                "#${index + 1} ${m.displayName} (${formatCount(m.weeklyCount)})"
            }.joinToString(" · ")

            val myRankIndex = top3.indexOfFirst { it.userId == currentUserId }
            if (myRankIndex >= 0) {
                val me = top3[myRankIndex]
                notices.add(
                    GroupCelebrationNotice(
                        id = "${group.id}_weekly_${weekKey}_private_$currentUserId",
                        groupId = group.id,
                        groupName = group.name,
                        type = GroupCelebrationType.WEEKLY_TOP_3_PRIVATE,
                        title = "You Ranked #${myRankIndex + 1} This Week!",
                        message = "Private celebration: مَا شَاءَ اللَّهُ ${me.displayName}, you read ${formatCount(me.weeklyCount)} $metricLabel this week in ${group.name}.",
                        targetUserId = currentUserId,
                        topMemberNames = top3.map { it.displayName },
                        createdAtMillis = nowMillis
                    )
                )
            }

            notices.add(
                GroupCelebrationNotice(
                    id = "${group.id}_weekly_${weekKey}_group",
                    groupId = group.id,
                    groupName = group.name,
                    type = GroupCelebrationType.WEEKLY_TOP_3_GROUP,
                    title = "Weekly Top 3 Readers",
                    message = "$summary — جَزَاكُمُ اللَّهُ خَيْرًا for leading ${group.name} this week!",
                    topMemberNames = top3.map { it.displayName },
                    createdAtMillis = nowMillis
                )
            )
        }

        return notices
    }
}
