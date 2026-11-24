package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.announcements.AnnouncementAction
import com.example.announcements.AnnouncementAudience
import com.example.announcements.AnnouncementFrequencyStore
import com.example.announcements.AnnouncementItem
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.appOutlinedTextFieldColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleAnnouncementDialog(
    onDismiss: () -> Unit,
    onSchedule: (AnnouncementItem) -> Unit,
    nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    val palette = AppThemeColors.palette
    val zoneId = remember { ZoneId.systemDefault() }
    val initialNow = remember {
        Instant.ofEpochMilli(nowProvider()).atZone(zoneId).toLocalDateTime().plusMinutes(2)
    }

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var isLinkType by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }
    var buttonText by remember { mutableStateOf("Join") }
    var imageUrl by remember { mutableStateOf("") }

    var selectedAudience by remember { mutableStateOf(AnnouncementAudience.ALL) }
    var inactiveDaysInput by remember {
        mutableStateOf(AnnouncementItem.DEFAULT_INACTIVE_DAYS_THRESHOLD.toString())
    }

    var isScheduledForLater by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf(initialNow.toLocalDate()) }
    var selectedHour by remember { mutableIntStateOf(initialNow.hour) }
    var selectedMinute by remember { mutableIntStateOf(initialNow.minute) }

    var isUrgent by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.US) }
    val scheduledDateTime = remember(selectedDate, selectedHour, selectedMinute) {
        LocalDateTime.of(selectedDate, LocalTime.of(selectedHour, selectedMinute))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 460.dp)
                .heightIn(max = 680.dp)
                .fillMaxWidth()
                .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
                .testTag("schedule_announcement_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Schedule / Send Announcement",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.onCard,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isUrgent) {
                        "URGENT: bypasses 10-min gap, 3/day and 21/week caps; shows immediately."
                    } else {
                        "Respects 10-min gap, max 3/day and 21/week caps."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUrgent) palette.accent else palette.onCardSubtext,
                    fontWeight = if (isUrgent) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.testTag("schedule_announcement_caps_hint")
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        validationError = null
                    },
                    label = { Text("Title (optional, emojis supported)") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_announcement_title_input")
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = {
                        body = it
                        validationError = null
                    },
                    label = { Text("Message text") },
                    minLines = 2,
                    maxLines = 4,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_announcement_body_input")
                )

                Text(
                    text = "ANNOUNCEMENT TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onCardSubtext
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonochromeOptionChip(
                        label = "Message Only",
                        selected = !isLinkType,
                        onClick = {
                            isLinkType = false
                            validationError = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("schedule_announcement_type_message_only")
                    )
                    MonochromeOptionChip(
                        label = "Message + Link",
                        selected = isLinkType,
                        onClick = {
                            isLinkType = true
                            validationError = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("schedule_announcement_type_link")
                    )
                }

                if (isLinkType) {
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = {
                            linkUrl = it
                            validationError = null
                        },
                        label = { Text("Link URL (https://...)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_announcement_link_input")
                    )

                    OutlinedTextField(
                        value = buttonText,
                        onValueChange = {
                            buttonText = it
                            validationError = null
                        },
                        label = { Text("Button Text (e.g. Join)") },
                        singleLine = true,
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_announcement_button_text_input")
                    )
                } else {
                    Text(
                        text = "Shows as a clean message pop-up with no action button.",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onCardSubtext
                    )
                }

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = {
                        imageUrl = it
                        validationError = null
                    },
                    label = { Text("Banner Image URL (optional https://...)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_announcement_image_url_input")
                )

                HorizontalDivider(color = palette.hairline)

                Text(
                    text = "TARGET AUDIENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onCardSubtext
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonochromeOptionChip(
                        label = "Everyone (All Users)",
                        selected = selectedAudience == AnnouncementAudience.ALL,
                        onClick = {
                            selectedAudience = AnnouncementAudience.ALL
                            validationError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_announcement_audience_all")
                    )
                    MonochromeOptionChip(
                        label = "Not Completed Daily Target Today",
                        selected = selectedAudience == AnnouncementAudience.INCOMPLETE_DAILY_TARGET,
                        onClick = {
                            selectedAudience = AnnouncementAudience.INCOMPLETE_DAILY_TARGET
                            validationError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_announcement_audience_incomplete")
                    )
                    MonochromeOptionChip(
                        label = "Inactive for X Days",
                        selected = selectedAudience == AnnouncementAudience.INACTIVE_DAYS,
                        onClick = {
                            selectedAudience = AnnouncementAudience.INACTIVE_DAYS
                            validationError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_announcement_audience_inactive")
                    )
                }

                if (selectedAudience == AnnouncementAudience.INACTIVE_DAYS) {
                    OutlinedTextField(
                        value = inactiveDaysInput,
                        onValueChange = { raw ->
                            inactiveDaysInput = raw.filter { it.isDigit() }.take(3)
                            validationError = null
                        },
                        label = { Text("Inactive Days Threshold (default 3)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_announcement_inactive_days_input")
                    )
                }

                HorizontalDivider(color = palette.hairline)

                Surface(
                    color = if (isUrgent) palette.subtleSurface else palette.cardSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isUrgent) palette.accent else palette.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isUrgent = !isUrgent
                            validationError = null
                        }
                        .minimumInteractiveComponentSize()
                        .testTag("schedule_announcement_urgent_toggle")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isUrgent,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = palette.accent,
                                uncheckedColor = palette.onCard,
                                checkmarkColor = palette.onAccent
                            )
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Urgent Announcement",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.onCard,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    color = if (isUrgent) palette.accent else palette.cardSurface,
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, if (isUrgent) palette.accent else palette.hairline),
                                    modifier = Modifier.testTag("schedule_announcement_urgent_badge")
                                ) {
                                    Text(
                                        text = "URGENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isUrgent) palette.onAccent else palette.onCardSubtext,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Bypass 10-min gap, 3/day & 21/week caps; shows immediately",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onCardSubtext
                            )
                        }
                    }
                }

                HorizontalDivider(color = palette.hairline)

                Text(
                    text = "SEND TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onCardSubtext
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonochromeOptionChip(
                        label = "Send Now",
                        selected = !isScheduledForLater,
                        onClick = {
                            isScheduledForLater = false
                            validationError = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("schedule_announcement_send_now")
                    )
                    MonochromeOptionChip(
                        label = "Schedule",
                        selected = isScheduledForLater,
                        onClick = {
                            isScheduledForLater = true
                            validationError = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("schedule_announcement_schedule_later")
                    )
                }

                if (isScheduledForLater) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WhiteBorderPillButton(
                            text = "+2 min test",
                            onClick = {
                                val target = Instant.ofEpochMilli(nowProvider())
                                    .atZone(zoneId)
                                    .toLocalDateTime()
                                    .plusMinutes(2)
                                selectedDate = target.toLocalDate()
                                selectedHour = target.hour
                                selectedMinute = target.minute
                                validationError = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("schedule_announcement_preset_2min")
                        )
                        WhiteBorderPillButton(
                            text = "Next الْجُمُعَةُ",
                            onClick = {
                                val today = Instant.ofEpochMilli(nowProvider())
                                    .atZone(zoneId)
                                    .toLocalDate()
                                val nextFriday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                                val candidate = LocalDateTime.of(nextFriday, LocalTime.of(12, 30))
                                val nowLocal = Instant.ofEpochMilli(nowProvider())
                                    .atZone(zoneId)
                                    .toLocalDateTime()
                                val resolved = if (candidate.isAfter(nowLocal)) {
                                    candidate
                                } else {
                                    candidate.plusWeeks(1)
                                }
                                selectedDate = resolved.toLocalDate()
                                selectedHour = resolved.hour
                                selectedMinute = resolved.minute
                                validationError = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("schedule_announcement_preset_jumuah")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WhiteBorderPillButton(
                            text = selectedDate.format(dateFormatter),
                            onClick = { showDatePicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("schedule_announcement_date_button")
                        )
                        WhiteBorderPillButton(
                            text = formatClockTime12h(selectedHour, selectedMinute),
                            onClick = { showTimePicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("schedule_announcement_time_button")
                        )
                    }

                    Text(
                        text = "Scheduled for: ${scheduledDateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.US))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onCard,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("schedule_announcement_scheduled_preview")
                    )
                }

                if (validationError != null) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.error,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("schedule_announcement_error")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("schedule_announcement_cancel_button")
                    ) {
                        Text("Cancel", color = palette.onCardSubtext)
                    }
                    BlackPillButton(
                        text = if (isScheduledForLater) "Schedule" else "Send Now",
                        onClick = {
                            val cleanTitle = title.trim()
                            val cleanBody = body.trim()
                            if (cleanTitle.isEmpty() && cleanBody.isEmpty()) {
                                validationError = "Please enter an announcement message."
                                return@BlackPillButton
                            }

                            val cleanLink = linkUrl.trim()
                            if (isLinkType && !isValidHttpUrl(cleanLink)) {
                                validationError = "Please enter a valid https:// link URL."
                                return@BlackPillButton
                            }

                            val cleanImage = imageUrl.trim()
                            if (cleanImage.isNotEmpty() && !isValidHttpUrl(cleanImage)) {
                                validationError = "Banner image URL must start with https:// or http://."
                                return@BlackPillButton
                            }

                            val inactiveDays = if (selectedAudience == AnnouncementAudience.INACTIVE_DAYS) {
                                inactiveDaysInput.toIntOrNull()?.takeIf { it >= 1 } ?: run {
                                    validationError = "Inactive days must be at least 1."
                                    return@BlackPillButton
                                }
                            } else {
                                AnnouncementItem.DEFAULT_INACTIVE_DAYS_THRESHOLD
                            }

                            val nowMillis = nowProvider()
                            val scheduledMillis = if (isScheduledForLater) {
                                val chosenMillis = scheduledDateTime
                                    .atZone(zoneId)
                                    .toInstant()
                                    .toEpochMilli()
                                if (chosenMillis <= nowMillis) {
                                    validationError = "Please choose a future date and time."
                                    return@BlackPillButton
                                }
                                chosenMillis
                            } else {
                                0L
                            }

                            val item = buildAnnouncementItemFromForm(
                                title = cleanTitle,
                                body = cleanBody,
                                isLinkType = isLinkType,
                                linkUrl = cleanLink,
                                buttonText = buttonText.trim().ifEmpty { "Join" },
                                imageUrl = cleanImage.ifEmpty { null },
                                targetAudience = selectedAudience,
                                inactiveDaysThreshold = inactiveDays,
                                scheduledAtMillis = scheduledMillis,
                                isUrgent = isUrgent,
                                nowMillis = nowMillis
                            )
                            onSchedule(item)
                        },
                        modifier = Modifier.testTag("schedule_announcement_submit_button")
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val initialUtcMillis = remember(selectedDate) {
            selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val todayUtcMillis = remember {
            LocalDate.now(zoneId).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialUtcMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= todayUtcMillis
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = palette.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = palette.cardSurface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = palette.cardSurface,
                    titleContentColor = palette.onCard,
                    headlineContentColor = palette.onCard,
                    weekdayContentColor = palette.onCardSubtext,
                    subheadContentColor = palette.onCard,
                    yearContentColor = palette.onCard,
                    currentYearContentColor = palette.onCard,
                    selectedYearContentColor = palette.onAccent,
                    selectedYearContainerColor = palette.accent,
                    dayContentColor = palette.onCard,
                    selectedDayContentColor = palette.onAccent,
                    selectedDayContainerColor = palette.accent,
                    todayContentColor = palette.accent,
                    todayDateBorderColor = palette.accent
                )
            )
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Select Send Time", color = palette.onCard) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(
                        state = timeState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = palette.subtleSurface,
                            selectorColor = palette.accent,
                            containerColor = palette.cardSurface,
                            periodSelectorBorderColor = palette.hairline,
                            periodSelectorSelectedContainerColor = palette.accent,
                            periodSelectorSelectedContentColor = palette.onAccent,
                            periodSelectorUnselectedContainerColor = palette.cardSurface,
                            periodSelectorUnselectedContentColor = palette.onCard,
                            timeSelectorSelectedContainerColor = palette.accent,
                            timeSelectorSelectedContentColor = palette.onAccent,
                            timeSelectorUnselectedContainerColor = palette.subtleSurface,
                            timeSelectorUnselectedContentColor = palette.onCard
                        )
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("Cancel", color = palette.onCardSubtext)
                    }
                    BlackPillButton(
                        text = "OK",
                        onClick = {
                            selectedHour = timeState.hour
                            selectedMinute = timeState.minute
                            showTimePicker = false
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun MonochromeOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    Surface(
        color = if (selected) palette.accent else palette.cardSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) palette.accent else palette.hairline),
        modifier = modifier
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) palette.onAccent else palette.onCard,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

internal fun isValidHttpUrl(url: String): Boolean {
    val clean = url.trim()
    return clean.startsWith("https://", ignoreCase = true) ||
        clean.startsWith("http://", ignoreCase = true)
}

internal fun buildAnnouncementItemFromForm(
    title: String,
    body: String,
    isLinkType: Boolean,
    linkUrl: String,
    buttonText: String,
    imageUrl: String?,
    targetAudience: AnnouncementAudience,
    inactiveDaysThreshold: Int,
    scheduledAtMillis: Long,
    isUrgent: Boolean = false,
    nowMillis: Long = System.currentTimeMillis()
): AnnouncementItem {
    val cleanTitle = title.trim()
    val cleanBody = body.trim()
    val cleanLink = linkUrl.trim()
    val cleanButtonText = buttonText.trim().ifBlank { "Join" }
    val cleanImage = imageUrl?.trim()?.takeIf { isValidHttpUrl(it) }
    val effectiveStart = if (scheduledAtMillis > 0L) scheduledAtMillis else nowMillis
    val suffix = Random.nextInt(1000, 9999)
    val id = "ann_${nowMillis}_$suffix"

    return AnnouncementItem(
        id = id,
        title = cleanTitle,
        body = cleanBody,
        imageUrl = cleanImage,
        buttonText = if (isLinkType && isValidHttpUrl(cleanLink)) cleanButtonText else null,
        buttonAction = if (isLinkType && isValidHttpUrl(cleanLink)) AnnouncementAction.LINK else AnnouncementAction.NONE,
        buttonUrl = if (isLinkType && isValidHttpUrl(cleanLink)) cleanLink else null,
        startAtMillis = effectiveStart,
        endAtMillis = effectiveStart + 7L * AnnouncementItem.ONE_DAY_MILLIS,
        maxShowsPerDay = AnnouncementFrequencyStore.configuredMaxShowsPerDay,
        maxShowsPerWeek = AnnouncementItem.DEFAULT_MAX_SHOWS_PER_WEEK,
        priority = if (isUrgent) 100 else 10,
        scheduledAtMillis = scheduledAtMillis,
        targetAudience = targetAudience,
        inactiveDaysThreshold = inactiveDaysThreshold.coerceAtLeast(1),
        isUrgent = isUrgent
    )
}

private fun formatClockTime12h(hour: Int, minute: Int): String {
    val amPm = if (hour >= 12) "PM" else "AM"
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.US, "%d:%02d %s", h12, minute, amPm)
}
