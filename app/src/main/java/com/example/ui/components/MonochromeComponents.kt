package com.example.ui.components

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FaintGridGrey
import com.example.ui.theme.HairlineGrey
import com.example.ui.theme.OffWhite
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SubtextGrey
import com.example.ui.theme.TrackGrey
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

@Volatile
private var lowRamOverrideForTest: Boolean? = null

fun setLowRamDeviceOverrideForTest(overrideValue: Boolean?) {
    lowRamOverrideForTest = overrideValue
}

fun isLowEndOrLowRamDevice(context: Context): Boolean {
    lowRamOverrideForTest?.let { return it }
    return try {
        val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        val lowCores = Runtime.getRuntime().availableProcessors() <= 2
        isLowRam || lowCores
    } catch (_: Exception) {
        false
    }
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

fun formatNumber(value: Long): String = NumberFormat.getNumberInstance(Locale.US).format(value)

fun formatSyncTime(millis: Long): String {
    if (millis <= 0L) return "Not synced yet"
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        else -> SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(millis))
    }
}

private data class CachedGridCell(
    val col: Int,
    val row: Int,
    val phaseOffset: Float
)

internal const val FAINT_GRID_SETTLE_DURATION_MS = 4_000
internal const val FAINT_GRID_OSCILLATION_AMPLITUDE_DP = 7f
private val CLEANER_GRID_HAIRLINE_COLOR = Color(0xFFF5F5F5)

@Composable
fun FaintGridBackground(
    modifier: Modifier = Modifier,
    gridSpacing: Dp = 32.dp,
    reduceMotion: Boolean = rememberReduceMotion(),
    driftDurationMs: Int = FAINT_GRID_SETTLE_DURATION_MS,
    pulseDurationMs: Int = FAINT_GRID_SETTLE_DURATION_MS,
    minSquareAlpha: Float = 0.004f,
    maxSquareAlphaDelta: Float = 0.012f
) {
    val context = LocalContext.current
    val isLowRam = remember(context) { isLowEndOrLowRamDevice(context) }
    val staticMode = reduceMotion || isLowRam
    val cycleHalfMs = (minOf(FAINT_GRID_SETTLE_DURATION_MS, driftDurationMs, pulseDurationMs) / 2)
        .coerceAtLeast(1_000)

    val oscillationFractionState = if (staticMode) {
        null
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "faint_grid_oscillation")
        infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = cycleHalfMs, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "faint_grid_y_fraction"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("faint_grid_background")
            .drawWithCache {
                val stepPx = gridSpacing.toPx().coerceAtLeast(8f)
                val amplitudePx = FAINT_GRID_OSCILLATION_AMPLITUDE_DP.dp.toPx()
                val cols = ceil(size.width / stepPx).toInt() + 2
                val rows = ceil(size.height / stepPx).toInt() + 3
                val cellSide = (stepPx - 2f).coerceAtLeast(1f)
                val cellSize = Size(cellSide, cellSide)

                val sparseCells = buildList {
                    for (c in -1 until cols) {
                        for (r in -2 until rows) {
                            val hash = ((c * 73856093) xor (r * 19349663)) and 0x7FFFFFFF
                            if (hash % 7 == 0) {
                                add(
                                    CachedGridCell(
                                        col = c,
                                        row = r,
                                        phaseOffset = (hash % 13) * 0.48f
                                    )
                                )
                            }
                        }
                    }
                }

                onDrawBehind {
                    drawRect(color = PureWhite)
                    // Read infinite animation value inside draw phase ONLY (zero recompositions)
                    val fraction = if (staticMode) 0f else (oscillationFractionState?.value ?: 0f)
                    val offsetYPx = fraction * amplitudePx
                    val pulsePhase = (fraction + 1f) * 0.5f * PI.toFloat()

                    for (i in sparseCells.indices) {
                        val cell = sparseCells[i]
                        val wave = (sin(pulsePhase + cell.phaseOffset) + 1f) * 0.5f
                        val alpha = minSquareAlpha + wave * maxSquareAlphaDelta
                        val left = cell.col * stepPx
                        val top = cell.row * stepPx + offsetYPx
                        if (top + cellSide >= 0f && top <= size.height) {
                            drawRect(
                                color = PureBlack.copy(alpha = alpha),
                                topLeft = Offset(left + 1f, top + 1f),
                                size = cellSize
                            )
                        }
                    }

                    var x = 0f
                    while (x <= size.width) {
                        drawLine(
                            color = CLEANER_GRID_HAIRLINE_COLOR,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += stepPx
                    }
                    var y = offsetYPx - stepPx
                    while (y <= size.height + stepPx) {
                        if (y >= 0f && y <= size.height) {
                            drawLine(
                                color = CLEANER_GRID_HAIRLINE_COLOR,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                        }
                        y += stepPx
                    }
                }
            }
    )
}

@Composable
fun BlackPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reduceMotion: Boolean = rememberReduceMotion(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    maxLines: Int = Int.MAX_VALUE
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 150, easing = FastOutSlowInEasing),
        label = "pill_scale"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PureBlack,
            contentColor = PureWhite,
            disabledContainerColor = HairlineGrey,
            disabledContentColor = SubtextGrey
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = contentPadding,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun WhiteBorderPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reduceMotion: Boolean = rememberReduceMotion(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    maxLines: Int = Int.MAX_VALUE
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 150, easing = FastOutSlowInEasing),
        label = "outlined_pill_scale"
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (enabled) PureBlack else HairlineGrey),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = PureWhite,
            contentColor = PureBlack,
            disabledContentColor = SubtextGrey
        ),
        contentPadding = contentPadding,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun formatPendingSyncSummary(
    pendingDuroodCount: Long,
    pendingDaysCount: Int
): String {
    return if (pendingDuroodCount > 0L && pendingDaysCount > 0) {
        val dayWord = if (pendingDaysCount == 1) "day" else "days"
        "${formatNumber(pendingDuroodCount)} Durood ($pendingDaysCount $dayWord) pending"
    } else if (pendingDuroodCount > 0L) {
        "${formatNumber(pendingDuroodCount)} Durood pending"
    } else if (pendingDaysCount > 0) {
        val dayWord = if (pendingDaysCount == 1) "day" else "days"
        "$pendingDaysCount $dayWord pending"
    } else {
        "Pending upload"
    }
}

fun formatOfflineSyncBadgeLabel(
    isOnline: Boolean,
    hasPendingSync: Boolean,
    isSyncing: Boolean,
    lastSyncedAtMillis: Long,
    pendingSyncDuroodCount: Long = 0L,
    pendingSyncDaysCount: Int = 0
): String {
    val pendingDetail = if (hasPendingSync && (pendingSyncDuroodCount > 0L || pendingSyncDaysCount > 0)) {
        formatPendingSyncSummary(pendingSyncDuroodCount, pendingSyncDaysCount)
    } else {
        null
    }
    return when {
        isSyncing -> "Syncing..."
        !isOnline -> if (pendingDetail != null) {
            "Offline · $pendingDetail · Saved on phone, will sync"
        } else {
            "Offline · Saved on phone, will sync"
        }
        hasPendingSync -> if (pendingDetail != null) {
            "Saved on phone · $pendingDetail, will sync"
        } else {
            "Saved on phone, will sync"
        }
        else -> "Synced · ${formatSyncTime(lastSyncedAtMillis)}"
    }
}

@Composable
fun OfflineSyncBadge(
    isOnline: Boolean,
    hasPendingSync: Boolean,
    isSyncing: Boolean,
    lastSyncedAtMillis: Long,
    modifier: Modifier = Modifier,
    pendingSyncDuroodCount: Long = 0L,
    pendingSyncDaysCount: Int = 0
) {
    val label = formatOfflineSyncBadgeLabel(
        isOnline = isOnline,
        hasPendingSync = hasPendingSync,
        isSyncing = isSyncing,
        lastSyncedAtMillis = lastSyncedAtMillis,
        pendingSyncDuroodCount = pendingSyncDuroodCount,
        pendingSyncDaysCount = pendingSyncDaysCount
    )
    val isFilledDot = isOnline && !hasPendingSync && !isSyncing

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .background(OffWhite, RoundedCornerShape(999.dp))
            .border(1.dp, HairlineGrey, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("offline_sync_badge")
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (isFilledDot) PureBlack else PureWhite, CircleShape)
                .border(1.dp, PureBlack, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (!isOnline || hasPendingSync) PureBlack else SubtextGrey
        )
    }
}

@Composable
fun RollingNumberText(
    value: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion()
) {
    val formatted = formatNumber(value)
    if (reduceMotion) {
        Text(
            text = formatted,
            style = style,
            color = PureBlack,
            modifier = modifier
        )
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        formatted.forEachIndexed { index, ch ->
            val placeFromRight = formatted.length - 1 - index
            key(placeFromRight) {
                if (ch.isDigit()) {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            (slideInVertically(
                                animationSpec = tween(180, easing = EaseOutCubic)
                            ) { height -> height / 2 } + fadeIn(tween(150, easing = EaseOutCubic)))
                                .togetherWith(
                                    slideOutVertically(
                                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                                    ) { height -> -height / 2 } + fadeOut(tween(120, easing = FastOutSlowInEasing))
                                )
                        },
                        label = "digit_$placeFromRight"
                    ) { digit ->
                        Text(
                            text = digit.toString(),
                            style = style,
                            color = PureBlack
                        )
                    }
                } else {
                    Text(
                        text = ch.toString(),
                        style = style,
                        color = PureBlack
                    )
                }
            }
        }
    }
}

@Composable
fun DailyProgressRing(
    todayCount: Long,
    dailyTarget: Int,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
    content: @Composable () -> Unit
) {
    DailyProgressRing(
        todayCount = todayCount,
        dailyTarget = dailyTarget.toLong(),
        modifier = modifier,
        reduceMotion = reduceMotion,
        content = content
    )
}

@Composable
fun DailyProgressRing(
    todayCount: Long,
    dailyTarget: Long,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
    content: @Composable () -> Unit
) {
    val targetLong = dailyTarget.coerceAtLeast(1L)
    val rawProgress = (todayCount.toDouble() / targetLong.toDouble()).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 250, easing = FastOutSlowInEasing),
        label = "ring_progress"
    )

    var prevCompleted by remember { mutableStateOf(todayCount >= targetLong) }
    var pulseTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(todayCount, targetLong) {
        val nowCompleted = todayCount >= targetLong
        if (!prevCompleted && nowCompleted && !reduceMotion) {
            pulseTrigger = true
            kotlinx.coroutines.delay(240L)
            pulseTrigger = false
        }
        prevCompleted = nowCompleted
    }

    val ringScale by animateFloatAsState(
        targetValue = if (pulseTrigger) 1.045f else 1f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 240, easing = FastOutSlowInEasing),
        label = "ring_pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.graphicsLayer {
            scaleX = ringScale
            scaleY = ringScale
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 6.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = TrackGrey,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (animatedProgress > 0f) {
                drawArc(
                    color = PureBlack,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}
