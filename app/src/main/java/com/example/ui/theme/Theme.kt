package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val MonochromeLightColorScheme = lightColorScheme(
    primary = PureBlack,
    onPrimary = PureWhite,
    primaryContainer = OffWhite,
    onPrimaryContainer = PureBlack,
    secondary = PureBlack,
    onSecondary = PureWhite,
    secondaryContainer = FaintGridGrey,
    onSecondaryContainer = PureBlack,
    tertiary = SubtextGrey,
    onTertiary = PureWhite,
    background = PureWhite,
    onBackground = PureBlack,
    surface = PureWhite,
    onSurface = PureBlack,
    surfaceVariant = OffWhite,
    onSurfaceVariant = SubtextGrey,
    outline = HairlineGrey,
    outlineVariant = FaintGridGrey,
    error = PureBlack,
    onError = PureWhite
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun OneCroreTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MonochromeLightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
