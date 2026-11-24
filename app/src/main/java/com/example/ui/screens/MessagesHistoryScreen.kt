package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.announcements.AnnouncementHistoryEntry
import com.example.announcements.AnnouncementItem
import com.example.ui.components.BlackPillButton
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.buildLinkifiedAnnouncementBody
import com.example.ui.theme.AppThemeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesHistoryScreen(
    entries: List<AnnouncementHistoryEntry>,
    onBack: () -> Unit,
    onActionClick: (AnnouncementItem) -> Unit
) {
    BackHandler(onBack = onBack)
    val palette = AppThemeColors.palette

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("messages_history_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = palette.onCanvas
                    )
                }
                Text(
                    text = "Messages History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.onCanvas,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("messages_history_title")
                )
            }

            HorizontalDivider(color = palette.hairline, thickness = 1.dp)

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                            .testTag("messages_history_empty")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No messages yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.onCard,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Community updates, milestone celebrations, and dismissed announcements will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.onCardSubtext
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("messages_history_list"),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = entries,
                        key = { it.announcement.id }
                    ) { entry ->
                        AnnouncementHistoryCard(
                            entry = entry,
                            onActionClick = onActionClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementHistoryCard(
    entry: AnnouncementHistoryEntry,
    onActionClick: (AnnouncementItem) -> Unit
) {
    val palette = AppThemeColors.palette
    val item = entry.announcement
    val formattedDate = remember(entry.recordedAtMillis) {
        formatHistoryTimestamp(entry.recordedAtMillis)
    }
    val linkifiedBody = remember(item.body, palette.accent) {
        buildLinkifiedAnnouncementBody(item.body, linkColor = palette.accent)
    }

    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .testTag("messages_history_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item.isUrgent || formattedDate.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.isUrgent) {
                        Surface(
                            color = palette.accent,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {}
                                .testTag("messages_history_urgent_badge_${item.id}")
                        ) {
                            Text(
                                text = "URGENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onAccent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (formattedDate.isNotBlank()) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.onCardSubtext,
                            modifier = Modifier.testTag("messages_history_date_${item.id}")
                        )
                    }
                }
            }

            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onCard,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("messages_history_item_title_${item.id}")
                )
            }

            if (item.body.isNotBlank()) {
                Text(
                    text = linkifiedBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onCard,
                    modifier = Modifier.testTag("messages_history_item_body_${item.id}")
                )
            }

            if (item.hasActionButton && !item.buttonText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                BlackPillButton(
                    text = item.buttonText,
                    onClick = { onActionClick(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("messages_history_action_${item.id}")
                )
            }
        }
    }
}

internal fun formatHistoryTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    return SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US).format(Date(timestampMillis))
}
