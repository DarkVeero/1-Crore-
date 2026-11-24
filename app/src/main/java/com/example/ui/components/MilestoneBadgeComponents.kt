package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.badges.MilestoneBadge
import com.example.badges.MilestoneBadgeManager
import com.example.badges.MilestoneBadgeProgress
import com.example.ui.theme.AppThemeColors

@Composable
fun HomeMilestoneBadgeCard(
    progress: MilestoneBadgeProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    val latest = progress.latestEarnedBadge
    val next = progress.nextBadge
    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("home_milestone_badges_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .background(if (latest != null) palette.accent else palette.subtleSurface, RoundedCornerShape(10.dp))
                        .border(1.dp, if (latest != null) palette.accent else palette.hairline, RoundedCornerShape(10.dp))
                ) {
                    if (latest != null) {
                        Text(
                            text = "#${latest.tier}",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.onAccent,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.MilitaryTech,
                            contentDescription = "Milestone badges",
                            tint = palette.onCard,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MILESTONE BADGES",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onCardSubtext
                        )
                        Surface(
                            color = if (progress.earnedCount > 0) palette.accent else palette.subtleSurface,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .border(1.dp, if (progress.earnedCount > 0) palette.accent else palette.hairline, RoundedCornerShape(999.dp))
                        ) {
                            Text(
                                text = "${progress.earnedCount} / ${progress.totalBadges}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (progress.earnedCount > 0) palette.onAccent else palette.onCard,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .testTag("home_milestone_badges_count")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (latest != null) {
                            "Latest: ${latest.title} (${formatNumber(latest.threshold)})"
                        } else {
                            "Earn a badge every 50,000 Durood"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCard,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("home_milestone_badges_latest")
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open all milestone badges in Stats",
                    tint = palette.onCard,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(palette.track, RoundedCornerShape(999.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            progress.progressToNextFraction.coerceAtLeast(
                                if (progress.progressToNextFraction > 0f) 0.02f else 0f
                            )
                        )
                        .fillMaxHeight()
                        .background(palette.accent, RoundedCornerShape(999.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (next != null) {
                        "${formatNumber(progress.remainingToNext)} left for ${next.shortLabel}"
                    } else {
                        "All 200 badges completed (1 Crore)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onCardSubtext
                )
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onCard,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun StatsMilestoneBadgesSection(
    progress: MilestoneBadgeProgress,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    var showAll200 by rememberSaveable { mutableStateOf(false) }

    val displayedBadges = remember(progress.earnedCount, showAll200) {
        if (showAll200) {
            MilestoneBadgeManager.ALL_BADGES
        } else {
            val earnedStart = (progress.earnedCount - 4).coerceAtLeast(1)
            val earnedSlice = if (progress.earnedCount > 0) {
                MilestoneBadgeManager.ALL_BADGES.subList(earnedStart - 1, progress.earnedCount)
            } else {
                emptyList()
            }
            val nextStart = progress.earnedCount
            val nextEnd = (nextStart + 8).coerceAtMost(MilestoneBadgeManager.TOTAL_BADGES)
            val upcomingSlice = if (nextStart < nextEnd) {
                MilestoneBadgeManager.ALL_BADGES.subList(nextStart, nextEnd)
            } else {
                emptyList()
            }
            (earnedSlice + upcomingSlice).distinctBy { it.tier }
        }
    }

    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .testTag("stats_milestone_badges_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MILESTONE BADGES",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onCardSubtext
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Every 50,000 Durood up to 1 Crore",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onCardSubtext
                    )
                }
                Surface(
                    color = palette.accent,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "${progress.earnedCount} / ${progress.totalBadges}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onAccent,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("stats_milestone_badges_count")
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(palette.track, RoundedCornerShape(999.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                progress.progressToNextFraction.coerceAtLeast(
                                    if (progress.progressToNextFraction > 0f) 0.02f else 0f
                                )
                            )
                            .fillMaxHeight()
                            .background(palette.accent, RoundedCornerShape(999.dp))
                    )
                }
                Text(
                    text = if (progress.nextBadge != null) {
                        "Next: ${progress.nextBadge.title} · ${formatNumber(progress.remainingToNext)} remaining"
                    } else {
                        "الْحَمْدُ لِلَّهِ! All 200 badges earned!"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onCardSubtext
                )
            }

            HorizontalDivider(color = palette.hairline, thickness = 1.dp)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displayedBadges.chunked(4).forEach { rowBadges ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowBadges.forEach { badge ->
                            val isEarned = badge.tier <= progress.earnedCount
                            BadgeGridTile(
                                badge = badge,
                                isEarned = isEarned,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(4 - rowBadges.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            WhiteBorderPillButton(
                text = if (showAll200) "Show compact view" else "View all 200 badges",
                onClick = { showAll200 = !showAll200 },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stats_badges_show_all_button")
            )
        }
    }
}

@Composable
private fun BadgeGridTile(
    badge: MilestoneBadge,
    isEarned: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    Surface(
        color = if (isEarned) palette.accent else palette.subtleSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (isEarned) palette.accent else palette.hairline,
                shape = RoundedCornerShape(10.dp)
            )
            .semantics(mergeDescendants = true) {}
            .testTag("stats_badge_tile_${badge.tier}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (isEarned) {
                Text(
                    text = "#${badge.tier}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onAccent.copy(alpha = 0.78f),
                    maxLines = 1
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = palette.onCardSubtext,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "#${badge.tier}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onCardSubtext,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = MilestoneBadgeManager.formatCompactChipLabel(badge.threshold),
                style = MaterialTheme.typography.labelMedium,
                color = if (isEarned) palette.onAccent else palette.onCard,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MilestoneBadgeCelebrationDialog(
    badge: MilestoneBadge,
    earnedCount: Int,
    onDismiss: () -> Unit
) {
    val palette = AppThemeColors.palette
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.scrim)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .testTag("milestone_badge_celebration_dialog"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = palette.cardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = palette.accent,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "BADGE #${badge.tier} OF 200",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("milestone_badge_dialog_close")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close badge celebration",
                                tint = palette.onCard,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .background(palette.accent, CircleShape)
                            .border(2.dp, palette.accent, CircleShape)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = MilestoneBadgeManager.formatCompactChipLabel(badge.threshold),
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.onAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "TIER ${badge.tier}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onAccent.copy(alpha = 0.78f)
                            )
                        }
                    }

                    Text(
                        text = "${badge.title} Unlocked!",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.onCard,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("milestone_badge_dialog_title")
                    )

                    Text(
                        text = "الْحَمْدُ لِلَّهِ! You have crossed ${badge.subtitle}. Earned $earnedCount of ${MilestoneBadgeManager.TOTAL_BADGES} badges.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCardSubtext,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    BlackPillButton(
                        text = "الْحَمْدُ لِلَّهِ",
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("milestone_badge_dialog_confirm")
                    )
                }
            }
        }
    }
}
