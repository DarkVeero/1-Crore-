package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.DuroodRepository
import com.example.ui.components.BlackPillButton
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.formatNumber
import com.example.ui.theme.HairlineGrey
import com.example.ui.theme.OffWhite
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SubtextGrey
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun SetupScreen(
    initialName: String,
    initialDailyTarget: Int = DuroodRepository.DEFAULT_DAILY_TARGET,
    onCompleteSetup: (displayName: String, dailyTarget: Int) -> Unit
) {
    var nameInput by remember(initialName) { mutableStateOf(initialName) }
    var dailyInput by remember(initialDailyTarget) { mutableStateOf(initialDailyTarget.toString()) }
    var yearsInput by remember(initialDailyTarget) {
        val yrs = DuroodRepository.ONE_CRORE.toDouble() / (initialDailyTarget.coerceAtLeast(1) * 365.0)
        mutableStateOf(String.format(Locale.US, "%.1f", yrs))
    }

    val parsedDaily = dailyInput.toIntOrNull()?.coerceIn(1, DuroodRepository.ONE_CRORE.toInt()) ?: 0
    val yearsValue = if (parsedDaily > 0) {
        DuroodRepository.ONE_CRORE.toDouble() / (parsedDaily * 365.0)
    } else {
        0.0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Text(
                    text = "FIRST STEP · SETUP",
                    style = MaterialTheme.typography.labelMedium,
                    color = SubtextGrey
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Plan your 1 Crore",
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureBlack
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Choose how many years you want to finish in, or enter your daily count — both calculate automatically. You can change this anytime in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubtextGrey
                )
            }

            Surface(
                color = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HairlineGrey, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= 60) nameInput = it },
                        label = { Text("Your Name (Display Name)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PureBlack,
                            unfocusedBorderColor = HairlineGrey,
                            focusedLabelColor = PureBlack,
                            cursorColor = PureBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_name_input")
                    )

                    OutlinedTextField(
                        value = yearsInput,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                yearsInput = filtered
                                val yrs = filtered.toDoubleOrNull()
                                if (yrs != null && yrs > 0.0) {
                                    val computedDaily = ceil(
                                        DuroodRepository.ONE_CRORE.toDouble() / (yrs * 365.0)
                                    ).toLong().coerceIn(1L, DuroodRepository.ONE_CRORE)
                                    dailyInput = computedDaily.toString()
                                }
                            }
                        },
                        label = { Text("Years to complete 1 Crore") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PureBlack,
                            unfocusedBorderColor = HairlineGrey,
                            focusedLabelColor = PureBlack,
                            cursorColor = PureBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_years_input")
                    )

                    OutlinedTextField(
                        value = dailyInput,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() }.take(8)
                            dailyInput = filtered
                            val daily = filtered.toLongOrNull()
                            if (daily != null && daily > 0L) {
                                val computedYears = DuroodRepository.ONE_CRORE.toDouble() / (daily * 365.0)
                                yearsInput = String.format(Locale.US, "%.1f", computedYears)
                            }
                        },
                        label = { Text("Daily Target") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PureBlack,
                            unfocusedBorderColor = HairlineGrey,
                            focusedLabelColor = PureBlack,
                            cursorColor = PureBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_daily_input")
                    )

                    Surface(
                        color = OffWhite,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HairlineGrey, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "DAILY TARGET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SubtextGrey
                                )
                                Text(
                                    text = if (parsedDaily > 0) "${formatNumber(parsedDaily.toLong())} / day" else "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PureBlack,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "ESTIMATED DURATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SubtextGrey
                                )
                                Text(
                                    text = if (yearsValue > 0.0) {
                                        val rounded = (yearsValue * 10.0).roundToInt() / 10.0
                                        "≈ $rounded years"
                                    } else {
                                        "—"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PureBlack
                                )
                            }
                        }
                    }
                }
            }

            BlackPillButton(
                text = "Start Journey",
                enabled = parsedDaily > 0 && nameInput.isNotBlank(),
                onClick = {
                    onCompleteSetup(nameInput.trim(), parsedDaily)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_start_button")
            )
        }
    }
}
