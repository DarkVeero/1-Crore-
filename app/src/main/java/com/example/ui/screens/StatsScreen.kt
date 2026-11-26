package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainUiState
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.StatsMilestoneBadgesSection
import com.example.ui.components.formatNumber
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.FaintGridGrey
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(uiState: MainUiState) {
    val palette = AppThemeColors.palette
    val best = uiState.bestDay
    val byDate = remember(uiState.allDays) { uiState.allDays.associateBy { it.dateKey } }
    val today = uiState.currentDate
    val last14Days = (13 downTo 0).map { offset ->
        val d = today.minusDays(offset.toLong())
        d to (byDate[d.toString()]?.count ?: 0L)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.onCanvas
                )
                Text(
                    text = "Consistency overview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onCanvasSubtext
                )
            }

            Surface(
                color = palette.cardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        StatCell(
                            label = "TARGET STREAK",
                            value = "${uiState.currentStreak} days",
                            subtext = "Meeting ${formatNumber(uiState.dailyTarget.toLong())}/day",
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(palette.hairline)
                        )
                        StatCell(
                            label = "BEST DAY",
                            value = if (best != null) formatNumber(best.count) else "0",
                            subtext = best?.let { formatDateLabel(it.dateKey, uiState.currentDate) } ?: "No data yet",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = palette.hairline, thickness = 1.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        StatCell(
                            label = "DAILY AVERAGE",
                            value = formatNumber(uiState.averagePerActiveDay),
                            subtext = "Per active day",
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(palette.hairline)
                        )
                        StatCell(
                            label = "ACTIVE DAYS",
                            value = "${uiState.activeDaysCount}",
                            subtext = "${formatNumber(uiState.totalCount)} total",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Surface(
                color = palette.cardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LAST 14 DAYS",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onCardSubtext
                        )
                        Text(
                            text = "Target: ${formatNumber(uiState.dailyTarget.toLong())}",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.onCardSubtext
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val maxVal = maxOf(
                        uiState.dailyTarget.toLong(),
                        last14Days.maxOfOrNull { it.second } ?: 0L,
                        1L
                    )
                    val chartGridLineColor = if (palette.themeMode == AppThemeMode.BLACK_AND_WHITE) {
                        FaintGridGrey
                    } else {
                        palette.hairline.copy(alpha = 0.7f)
                    }
                    val targetLineColor = palette.onCardSubtext
                    val barFillColor = palette.accent
                    val emptyBarColor = palette.hairline

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        // Faint horizontal gridlines
                        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { ratio ->
                            val y = size.height * (1f - ratio)
                            drawLine(
                                color = chartGridLineColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Dashed daily target reference line
                        val targetY = size.height * (1f - (uiState.dailyTarget.toFloat() / maxVal.toFloat()))
                        drawLine(
                            color = targetLineColor,
                            start = Offset(0f, targetY),
                            end = Offset(size.width, targetY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )

                        // Bars
                        val slotWidth = size.width / last14Days.size
                        val barWidth = slotWidth * 0.55f
                        last14Days.forEachIndexed { index, (_, count) ->
                            val ratio = (count.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                            val barHeight = size.height * ratio
                            val left = index * slotWidth + (slotWidth - barWidth) / 2f
                            if (barHeight > 0f) {
                                drawRect(
                                    color = barFillColor,
                                    topLeft = Offset(left, size.height - barHeight),
                                    size = Size(barWidth, barHeight)
                                )
                            } else {
                                drawRect(
                                    color = emptyBarColor,
                                    topLeft = Offset(left, size.height - 2.dp.toPx()),
                                    size = Size(barWidth, 2.dp.toPx())
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        last14Days.forEachIndexed { index, (date, _) ->
                            val label = if (index == 0 || index == 6 || index == 13) {
                                date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US)
                            } else {
                                ""
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onCardSubtext
                            )
                        }
                    }
                }
            }

            StatsMilestoneBadgesSection(progress = uiState.badgeProgress)
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    Column(
        modifier = modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.onCardSubtext
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.onCard,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtext,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onCardSubtext
        )
    }
}
