package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomCounterEntity
import com.example.ui.MainUiState
import com.example.ui.components.BlackPillButton
import com.example.ui.components.DailyProgressRing
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.OfflineSyncBadge
import com.example.ui.components.RollingNumberText
import com.example.ui.components.WhiteBorderPillButton
import com.example.ui.components.formatNumber
import com.example.ui.components.rememberReduceMotion
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.appOutlinedTextFieldColors
import java.util.Locale

@Composable
fun CustomCountersScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onOpenCounter: (String) -> Unit,
    onCreateCounter: (name: String, targetCount: Long) -> Unit,
    onUpdateCounter: (counterId: String, name: String, targetCount: Long, currentCount: Long, countAtDialogOpen: Long) -> Unit,
    onDeleteCounter: (counterId: String) -> Unit
) {
    val palette = AppThemeColors.palette
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingCounter by remember { mutableStateOf<CustomCounterEntity?>(null) }
    var deletingCounter by remember { mutableStateOf<CustomCounterEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("custom_counters_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.onCanvas
                        )
                    }
                    Column {
                        Text(
                            text = "Custom Counters",
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.onCanvas,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Separate counters with your own target",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onCanvasSubtext
                        )
                    }
                }
                BlackPillButton(
                    text = "+ New",
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_custom_counter_button")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.customCounters.isEmpty()) {
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No custom counters yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.onCard,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Create a dedicated counter with any name and target count. Counts here stay completely separate from your 1 Crore total.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onCardSubtext
                        )
                        BlackPillButton(
                            text = "Create Custom Counter",
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.customCounters, key = { it.id }) { counter ->
                        CustomCounterListItem(
                            counter = counter,
                            onClick = { onOpenCounter(counter.id) },
                            onEdit = { editingCounter = counter },
                            onDelete = { deletingCounter = counter }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrEditCustomCounterDialog(
            title = "New Custom Counter",
            confirmLabel = "Create",
            initialName = "",
            initialTarget = "",
            initialCount = null,
            onDismiss = { showCreateDialog = false },
            onSave = { name, target, _ ->
                onCreateCounter(name, target)
                showCreateDialog = false
            }
        )
    }

    val toEdit = editingCounter
    if (toEdit != null) {
        CreateOrEditCustomCounterDialog(
            title = "Edit Counter",
            confirmLabel = "Save",
            initialName = toEdit.name,
            initialTarget = toEdit.targetCount.toString(),
            initialCount = toEdit.currentCount,
            onDismiss = { editingCounter = null },
            onSave = { name, target, count ->
                // R3-P2-35: toEdit is the snapshot captured when the dialog
                // opened — its count is the open-time count the delta is
                // measured against.
                onUpdateCounter(toEdit.id, name, target, count ?: toEdit.currentCount, toEdit.currentCount)
                editingCounter = null
            }
        )
    }

    val toDelete = deletingCounter
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingCounter = null },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("delete_custom_counter_dialog"),
            title = {
                Text(
                    text = "Delete Zikr?",
                    color = palette.onCard,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("delete_custom_counter_dialog_title")
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = toDelete.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onCard,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("delete_custom_counter_name")
                    )
                    Text(
                        text = "Total count: ${formatNumber(toDelete.currentCount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCard,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("delete_custom_counter_count")
                    )
                    Text(
                        text = "This will permanently delete this zikr and its counts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCardSubtext,
                        modifier = Modifier.testTag("delete_custom_counter_warning")
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { deletingCounter = null },
                        modifier = Modifier.testTag("cancel_delete_custom_counter")
                    ) {
                        Text("Cancel", color = palette.onCardSubtext)
                    }
                    BlackPillButton(
                        text = "Delete",
                        onClick = {
                            onDeleteCounter(toDelete.id)
                            deletingCounter = null
                        },
                        modifier = Modifier.testTag("confirm_delete_custom_counter")
                    )
                }
            }
        )
    }
}

@Composable
private fun CustomCounterListItem(
    counter: CustomCounterEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = AppThemeColors.palette
    val fraction = (counter.currentCount.toDouble() / counter.targetCount.coerceAtLeast(1L).toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
    val percentLabel = String.format(Locale.US, "%.1f%%", fraction * 100f)

    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("custom_counter_card_${counter.id}")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = counter.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.onCard,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatNumber(counter.currentCount)} / ${formatNumber(counter.targetCount)} · $percentLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCardSubtext
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("custom_counter_rename_${counter.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Rename ${counter.name}",
                            tint = palette.onCard
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("custom_counter_delete_${counter.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete ${counter.name}",
                            tint = palette.onCard
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(palette.track, RoundedCornerShape(999.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(if (counter.currentCount > 0L) 0.01f else 0f))
                        .fillMaxHeight()
                        .background(palette.accent, RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
fun CustomCounterDetailScreen(
    counterId: String,
    uiState: MainUiState,
    onBack: () -> Unit,
    onTapPlusOne: () -> Unit,
    onAddCustomAmount: (Long) -> Unit,
    onUpdateCounter: (name: String, targetCount: Long, currentCount: Long, countAtDialogOpen: Long) -> Unit
) {
    val palette = AppThemeColors.palette
    val counter = uiState.customCounters.firstOrNull { it.id == counterId }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()

    var showCustomAmountDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (counter == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            FaintGridBackground()
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Counter not found",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.onCanvas
                )
                BlackPillButton(text = "Go Back", onClick = onBack)
            }
        }
        return
    }

    val tapInteraction = remember { MutableInteractionSource() }
    val tapPressed by tapInteraction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (tapPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 150, easing = FastOutSlowInEasing),
        label = "custom_tap_scale"
    )

    val progressFraction = (counter.currentCount.toDouble() / counter.targetCount.coerceAtLeast(1L).toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
    val percentLabel = String.format(Locale.US, "%.1f%%", progressFraction * 100f)
    val remaining = (counter.targetCount - counter.currentCount).coerceAtLeast(0L)

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground(reduceMotion = reduceMotion)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("custom_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.onCanvas
                        )
                    }
                    OfflineSyncBadge(
                        isOnline = uiState.isOnline,
                        hasPendingSync = uiState.hasPendingSync,
                        isSyncing = uiState.isSyncing,
                        lastSyncedAtMillis = uiState.profile?.lastSyncedAtMillis ?: 0L,
                        pendingSyncDuroodCount = uiState.pendingSyncDuroodCount,
                        pendingSyncDaysCount = uiState.pendingSyncDaysCount
                    )
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Counter",
                            tint = palette.onCanvas
                        )
                    }
                }

                Text(
                    text = counter.name.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onCanvasSubtext,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                RollingNumberText(
                    value = counter.currentCount,
                    style = MaterialTheme.typography.displayMedium,
                    reduceMotion = reduceMotion,
                    color = palette.onCanvas
                )

                Text(
                    text = if (remaining == 0L) {
                        "Target: ${formatNumber(counter.targetCount)} · Complete"
                    } else {
                        "Target: ${formatNumber(counter.targetCount)} · $percentLabel (${formatNumber(remaining)} left)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (remaining == 0L) palette.onCanvas else palette.onCanvasSubtext,
                    fontWeight = if (remaining == 0L) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            DailyProgressRing(
                todayCount = counter.currentCount,
                dailyTarget = counter.targetCount,
                reduceMotion = reduceMotion,
                modifier = Modifier.size(264.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(216.dp)
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                        .clip(CircleShape)
                        .background(palette.accent)
                        .clickable(
                            interactionSource = tapInteraction,
                            indication = null,
                            role = Role.Button
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val willComplete = (counter.currentCount + 1L) == counter.targetCount
                            triggerCustomTapVibration(context, strong = willComplete)
                            onTapPlusOne()
                        }
                        .semantics { contentDescription = "Tap to add 1 to ${counter.name}" }
                        .testTag("custom_counter_tap_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "+1",
                            style = MaterialTheme.typography.displayLarge,
                            color = palette.onAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "TAP",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.onAccent.copy(alpha = 0.78f)
                        )
                    }
                }
            }

            WhiteBorderPillButton(
                text = "+ Custom Amount",
                onClick = { showCustomAmountDialog = true },
                reduceMotion = reduceMotion,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .testTag("custom_counter_amount_button")
            )
        }
    }

    if (showCustomAmountDialog) {
        CustomCounterAmountDialog(
            counterName = counter.name,
            onDismiss = { showCustomAmountDialog = false },
            onConfirm = { amount ->
                triggerCustomTapVibration(context, strong = true)
                onAddCustomAmount(amount)
                showCustomAmountDialog = false
            }
        )
    }

    if (showEditDialog) {
        // R3-P2-35: capture the count once, when the dialog opens. Passing
        // the live counter.currentCount as initialCount would re-init the
        // dialog's input on every tap made while it is open (remember is
        // keyed on initialCount) and lose the open-time baseline.
        val openCount = remember { counter.currentCount }
        CreateOrEditCustomCounterDialog(
            title = "Edit Counter",
            confirmLabel = "Save",
            initialName = counter.name,
            initialTarget = counter.targetCount.toString(),
            initialCount = openCount,
            onDismiss = { showEditDialog = false },
            onSave = { name, target, count ->
                onUpdateCounter(name, target, count ?: openCount, openCount)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun CreateOrEditCustomCounterDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialTarget: String,
    initialCount: Long?,
    onDismiss: () -> Unit,
    onSave: (name: String, targetCount: Long, currentCount: Long?) -> Unit
) {
    val palette = AppThemeColors.palette
    var nameInput by remember(initialName) { mutableStateOf(initialName) }
    var targetInput by remember(initialTarget) { mutableStateOf(initialTarget) }
    var countInput by remember(initialCount) { mutableStateOf(initialCount?.toString().orEmpty()) }

    val cleanName = nameInput.trim()
    val parsedTarget = targetInput.toLongOrNull()?.takeIf { it in 1L..100_000_000L }
    val parsedCount = if (initialCount != null) {
        countInput.toLongOrNull()?.takeIf { it in 0L..100_000_000L }
    } else {
        0L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = palette.onCard
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 80) nameInput = it },
                    label = { Text("Counter Name") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_counter_name_input")
                )
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { raw -> targetInput = raw.filter { it.isDigit() }.take(9) },
                    label = { Text("Target Count") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_counter_target_input")
                )
                if (initialCount != null) {
                    OutlinedTextField(
                        value = countInput,
                        onValueChange = { raw -> countInput = raw.filter { it.isDigit() }.take(9) },
                        label = { Text("Current Count") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_counter_current_input")
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
                BlackPillButton(
                    text = confirmLabel,
                    enabled = cleanName.isNotEmpty() && parsedTarget != null && parsedCount != null,
                    onClick = {
                        if (cleanName.isNotEmpty() && parsedTarget != null && parsedCount != null) {
                            onSave(cleanName, parsedTarget, parsedCount)
                        }
                    },
                    modifier = Modifier.testTag("save_custom_counter_button")
                )
            }
        }
    )
}

@Composable
private fun CustomCounterAmountDialog(
    counterName: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val palette = AppThemeColors.palette
    var input by remember { mutableStateOf("") }
    val parsed = input.toLongOrNull()?.takeIf { it in 1L..1_000_000L }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Add Custom Amount",
                style = MaterialTheme.typography.titleLarge,
                color = palette.onCard
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "How many would you like to add to $counterName?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onCardSubtext
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { raw -> input = raw.filter { it.isDigit() }.take(7) },
                    label = { Text("Enter Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_counter_amount_input")
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
                BlackPillButton(
                    text = "Add",
                    enabled = parsed != null,
                    onClick = { parsed?.let(onConfirm) },
                    modifier = Modifier.testTag("confirm_custom_counter_amount_button")
                )
            }
        }
    )
}

private fun triggerCustomTapVibration(context: Context, strong: Boolean) {
    try {
        val durationMs = if (strong) 45L else 18L
        val amplitude = if (strong) 180 else 70
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, amplitude)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
    } catch (_: Exception) {
    }
}
