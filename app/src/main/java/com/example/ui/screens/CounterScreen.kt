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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.example.ui.MainUiState
import com.example.ui.components.BlackPillButton
import com.example.ui.components.DailyProgressRing
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.OfflineSyncBadge
import com.example.ui.components.RollingNumberText
import com.example.ui.components.formatNumber
import com.example.ui.components.rememberReduceMotion
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.appOutlinedTextFieldColors

@Composable
fun CounterScreen(
    uiState: MainUiState,
    onTapPlusOne: () -> Unit,
    onAddCustomAmount: (Long) -> Unit,
    onOpenCustomCounters: () -> Unit,
    initialShowCustomDialog: Boolean = false,
    onConsumedInitialCustomDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val palette = AppThemeColors.palette
    var showCustomDialog by remember { mutableStateOf(initialShowCustomDialog) }

    LaunchedEffect(initialShowCustomDialog) {
        if (initialShowCustomDialog) {
            showCustomDialog = true
            onConsumedInitialCustomDialog()
        }
    }

    val tapInteraction = remember { MutableInteractionSource() }
    val tapPressed by tapInteraction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (tapPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 150, easing = FastOutSlowInEasing),
        label = "tap_scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground(reduceMotion = reduceMotion)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OfflineSyncBadge(
                    isOnline = uiState.isOnline,
                    hasPendingSync = uiState.hasPendingSync,
                    isSyncing = uiState.isSyncing,
                    lastSyncedAtMillis = uiState.profile?.lastSyncedAtMillis ?: 0L,
                    pendingSyncDuroodCount = uiState.pendingSyncDuroodCount,
                    pendingSyncDaysCount = uiState.pendingSyncDaysCount
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TOTAL DUROOD PAK",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onCanvasSubtext
                )
                RollingNumberText(
                    value = uiState.totalCount,
                    style = MaterialTheme.typography.displayMedium,
                    reduceMotion = reduceMotion,
                    color = palette.onCanvas
                )
                Text(
                    text = "Today: ${formatNumber(uiState.todayCount)} / ${formatNumber(uiState.dailyTarget.toLong())}" +
                        if (uiState.todayCount >= uiState.dailyTarget) " · Target Complete" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.todayCount >= uiState.dailyTarget) palette.onCanvas else palette.onCanvasSubtext,
                    fontWeight = if (uiState.todayCount >= uiState.dailyTarget) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            DailyProgressRing(
                todayCount = uiState.todayCount,
                dailyTarget = uiState.dailyTarget,
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
                            val willCompleteTarget = (uiState.todayCount + 1L) == uiState.dailyTarget.toLong()
                            triggerTapVibration(context, strong = willCompleteTarget)
                            onTapPlusOne()
                        }
                        .semantics { contentDescription = "Tap to add 1 Durood Pak" }
                        .testTag("counter_tap_button")
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(0.84f)
            ) {
                // Round 1 C-P2-2: the CustomAmountDialog below was dead code
                // — nothing on this tab could open it (the widget's custom
                // action opens it on Home instead). This button makes the
                // dialog reachable from the Counter tab itself, like Home's
                // "Custom Add" button.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showCustomDialog = true },
                        modifier = Modifier.testTag("counter_custom_add_button")
                    ) {
                        Text(
                            text = "Custom Add",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.onCanvas,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    TextButton(
                        onClick = onOpenCustomCounters,
                        modifier = Modifier.testTag("counter_open_custom_counters_button")
                    ) {
                        Text(
                            text = "Custom Counters",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.onCanvas,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomAmountDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { amount ->
                triggerTapVibration(context, strong = true)
                onAddCustomAmount(amount)
                showCustomDialog = false
            }
        )
    }
}

@Composable
internal fun CustomAmountDialog(
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
                    text = "How many would you like to add to today's count?",
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
                        .testTag("custom_amount_input")
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
                    modifier = Modifier.testTag("confirm_custom_amount_button")
                )
            }
        }
    )
}

internal fun triggerTapVibration(context: Context, strong: Boolean) {
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
