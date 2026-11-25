package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.DailyCountEntity
import com.example.ui.MainUiState
import com.example.ui.components.BlackPillButton
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.WhiteBorderPillButton
import com.example.ui.components.formatNumber
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.appOutlinedTextFieldColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class PendingReduction(
    val dateKey: String,
    val newCount: Long,
        val oldCount: Long,
    val oldTotal: Long,
    val newTotal: Long
)

@Composable
fun HistoryScreen(
    uiState: MainUiState,
    historyDays: List<DailyCountEntity>,
    onEditDayCount: (dateKey: String, newCount: Long, oldCount: Long) -> Unit,
    onOpenMessagesHistory: () -> Unit = {}
) {
    val palette = AppThemeColors.palette
    var editingDateKey by remember { mutableStateOf<String?>(null) }
    var editingInitialCount by remember { mutableStateOf(0L) }
    var showAddDateDialog by remember { mutableStateOf(false) }
    var pendingReduction by remember { mutableStateOf<PendingReduction?>(null) }

    val sortedDays = historyDays.sortedByDescending { it.dateKey }

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .widthIn(max = 600.dp)
                .padding(top = 20.dp)
                .align(Alignment.TopCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.onCanvas
                )
                Text(
                    text = "Daily record · Edit anytime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onCanvasSubtext
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WhiteBorderPillButton(
                    text = "Messages History",
                    onClick = onOpenMessagesHistory,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_messages_history_button")
                )
                WhiteBorderPillButton(
                    text = "+ Log Date",
                    onClick = { showAddDateDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_log_date_button")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (sortedDays.isEmpty()) {
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No counts saved yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.onCard
                        )
                        Text(
                            text = "Tap on the Counter or use 'Log Date' to record earlier days.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onCardSubtext
                        )
                    }
                }
            } else {
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sortedDays, key = { it.dateKey }) { day ->
                            HistoryRow(
                                day = day,
                                today = uiState.currentDate,
                                dailyTarget = uiState.dailyTarget,
                                onEdit = {
                                    editingDateKey = day.dateKey
                                    editingInitialCount = day.count
                                }
                            )
                            HorizontalDivider(color = palette.hairline, thickness = 1.dp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val activeEditKey = editingDateKey
    if (activeEditKey != null) {
        EditDayCountDialog(
            dateKey = activeEditKey,
            today = uiState.currentDate,
            currentCount = editingInitialCount,
            onDismiss = { editingDateKey = null },
            onSave = { newCount ->
                val safeNew = newCount.coerceAtLeast(0L)
                if (safeNew < editingInitialCount) {
                    val oldTotal = uiState.totalCount
                    val newTotal = (oldTotal - editingInitialCount + safeNew).coerceAtLeast(0L)
                    pendingReduction = PendingReduction(
                        dateKey = activeEditKey,
                        oldCount = editingInitialCount,
                        newCount = safeNew,
                        oldTotal = oldTotal,
                        newTotal = newTotal
                    )
                } else {
                    onEditDayCount(activeEditKey, safeNew, editingInitialCount)
                    editingDateKey = null
                }
            }
        )
    }

    if (showAddDateDialog) {
        val existingByDate = sortedDays.associate { it.dateKey to it.count }
        AddPastDateDialog(
            today = uiState.currentDate,
            existingByDate = existingByDate,
            onDismiss = { showAddDateDialog = false },
            onSave = { dateKey, count ->
                val safeNew = count.coerceAtLeast(0L)
                val oldDayCount = existingByDate[dateKey] ?: 0L
                if (safeNew < oldDayCount) {
                    val oldTotal = uiState.totalCount
                    val newTotal = (oldTotal - oldDayCount + safeNew).coerceAtLeast(0L)
                    pendingReduction = PendingReduction(
                        dateKey = dateKey,
                        oldCount = oldDayCount,
                        newCount = safeNew,
                        oldTotal = oldTotal,
                        newTotal = newTotal
                    )
                } else {
                    onEditDayCount(dateKey, safeNew, oldDayCount)
                    showAddDateDialog = false
                }
            }
        )
    }

    val reduction = pendingReduction
    if (reduction != null) {
        AlertDialog(
            onDismissRequest = { pendingReduction = null },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Confirm Reduction",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.onCard,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Are you sure? This will reduce your total from ${formatNumber(uiState.totalCount)} to ${formatNumber((uiState.totalCount - reduction.oldCount + reduction.newCount).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onCard
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { pendingReduction = null },
                        modifier = Modifier.testTag("cancel_reduce_day_button")
                    ) {
                        Text("Cancel", color = palette.onCardSubtext)
                    }
                    BlackPillButton(
                        text = "Yes, reduce",
                        onClick = {
                            onEditDayCount(reduction.dateKey, reduction.newCount, reduction.oldCount)
                            pendingReduction = null
                            editingDateKey = null
                            showAddDateDialog = false
                        },
                        modifier = Modifier.testTag("confirm_reduce_day_button")
                    )
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    day: DailyCountEntity,
    today: LocalDate,
    dailyTarget: Int,
    onEdit: () -> Unit
) {
    val palette = AppThemeColors.palette
    val dateLabel = formatDateLabel(day.dateKey, today)
    val targetMet = day.count >= dailyTarget.toLong()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("history_row_${day.dateKey}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onCard,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = buildString {
                    append(if (targetMet) "Target complete" else "${formatNumber((dailyTarget - day.count).coerceAtLeast(0L))} short of target")
                    if (day.pendingSync) append(" · Pending sync")
                },
                style = MaterialTheme.typography.labelMedium,
                color = palette.onCardSubtext
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatNumber(day.count),
                style = MaterialTheme.typography.titleLarge,
                color = palette.onCard,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_day_${day.dateKey}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit count for ${day.dateKey}",
                    tint = palette.onCard
                )
            }
        }
    }
}

@Composable
private fun EditDayCountDialog(
    dateKey: String,
    today: LocalDate,
    currentCount: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    val palette = AppThemeColors.palette
    var input by remember(dateKey) { mutableStateOf(currentCount.toString()) }
    val parsed = input.toLongOrNull()?.takeIf { it in 0L..100_000_000L }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Edit ${formatDateLabel(dateKey, today)}",
                style = MaterialTheme.typography.titleLarge,
                color = palette.onCard
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { raw ->
                        input = raw.filter { it.isDigit() }.take(9)
                    },
                    label = { Text("Durood Count") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_day_count_input")
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
                BlackPillButton(
                    text = "Save",
                    enabled = parsed != null,
                    onClick = {
                        val value = parsed ?: return@BlackPillButton
                        onSave(value)
                    },
                    modifier = Modifier.testTag("save_day_count_button")
                )
            }
        }
    )
}

@Composable
private fun AddPastDateDialog(
    today: LocalDate,
    existingByDate: Map<String, Long>,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    val palette = AppThemeColors.palette
    var dateInput by remember(today) { mutableStateOf(today.minusDays(1).toString()) }
    var countInput by remember { mutableStateOf("") }
    val validDate = try {
        val d = LocalDate.parse(dateInput.trim())
        !d.isAfter(today)
    } catch (_: Exception) {
        false
    }
    val parsedCount = countInput.toLongOrNull()?.takeIf { it in 0L..100_000_000L }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Log Past Date",
                style = MaterialTheme.typography.titleLarge,
                color = palette.onCard
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it.take(10) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = countInput,
                    onValueChange = { raw -> countInput = raw.filter { it.isDigit() }.take(9) },
                    label = {
                        val existing = existingByDate[dateInput.trim()]
                        Text(if (existing != null) "Count (current: ${formatNumber(existing)})" else "Count")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
                BlackPillButton(
                    text = "Save",
                    enabled = validDate && parsedCount != null,
                    onClick = {
                        if (validDate && parsedCount != null) {
                            onSave(dateInput.trim(), parsedCount)
                        }
                    }
                )
            }
        }
    )
}

fun formatDateLabel(dateKey: String, today: LocalDate = LocalDate.now()): String {
    return try {
        val date = LocalDate.parse(dateKey)
        val formatted = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
        when (date) {
            today -> "Today · $formatted"
            today.minusDays(1) -> "Yesterday · $formatted"
            else -> formatted
        }
    } catch (_: Exception) {
        dateKey
    }
}
