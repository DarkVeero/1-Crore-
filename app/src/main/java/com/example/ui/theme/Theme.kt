package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class AppColorPalette(
    val themeMode: AppThemeMode,
    val canvasBackground: Color,
    val canvasGridHairline: Color,
    val onCanvas: Color,
    val onCanvasSubtext: Color,
    val cardSurface: Color,
    val subtleSurface: Color,
    val onCard: Color,
    val onCardSubtext: Color,
    val hairline: Color,
    val track: Color,
    val strongBorder: Color,
    val accent: Color,
    val onAccent: Color,
    val navContainer: Color,
    val navDivider: Color,
    val navSelectedIcon: Color,
    val navSelectedText: Color,
    val navIndicator: Color,
    val navUnselected: Color,
    val splashWave: Color,
    val scrim: Color,
    val error: Color,
    val onError: Color
)

val BlackAndWhiteAppPalette = AppColorPalette(
    themeMode = AppThemeMode.BLACK_AND_WHITE,
    canvasBackground = PureWhite,
    canvasGridHairline = CleanerGridHairline,
    onCanvas = PureBlack,
    onCanvasSubtext = SubtextGrey,
    cardSurface = PureWhite,
    subtleSurface = OffWhite,
    onCard = PureBlack,
    onCardSubtext = SubtextGrey,
    hairline = HairlineGrey,
    track = TrackGrey,
    strongBorder = PureBlack,
    accent = PureBlack,
    onAccent = PureWhite,
    navContainer = PureWhite,
    navDivider = HairlineGrey,
    navSelectedIcon = PureBlack,
    navSelectedText = PureBlack,
    navIndicator = FaintGridGrey,
    navUnselected = SubtextGrey,
    splashWave = PureBlack,
    scrim = PureBlack.copy(alpha = 0.45f),
    error = ErrorRed,
    onError = PureWhite
)

val RedAppPalette = AppColorPalette(
    themeMode = AppThemeMode.RED,
    canvasBackground = CharcoalBlack,
    canvasGridHairline = CharcoalGridHairline,
    onCanvas = BoneWhite,
    onCanvasSubtext = CharcoalSubtext,
    cardSurface = BoneWhite,
    subtleSurface = BoneWhiteSubtle,
    onCard = CharcoalBlack,
    onCardSubtext = BoneWhiteMutedInk,
    hairline = BoneWhiteHairline,
    track = BoneWhiteTrack,
    strongBorder = CharcoalBlack,
    accent = BarberRed,
    onAccent = BoneWhite,
    navContainer = CharcoalBlack,
    navDivider = CharcoalDivider,
    navSelectedIcon = BarberRed,
    navSelectedText = BoneWhite,
    navIndicator = BarberRed.copy(alpha = 0.18f),
    navUnselected = CharcoalSubtext,
    splashWave = CharcoalBlack,
    scrim = PureBlack.copy(alpha = 0.58f),
    error = BarberRed,
    onError = BoneWhite
)

val LocalAppPalette = staticCompositionLocalOf { BlackAndWhiteAppPalette }

object AppThemeColors {
    val palette: AppColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppPalette.current
}

@Composable
fun appOutlinedTextFieldColors(): TextFieldColors {
    val palette = AppThemeColors.palette
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.hairline,
        focusedLabelColor = palette.accent,
        unfocusedLabelColor = palette.onCardSubtext,
        focusedTextColor = palette.onCard,
        unfocusedTextColor = palette.onCard,
        focusedPlaceholderColor = palette.onCardSubtext,
        unfocusedPlaceholderColor = palette.onCardSubtext,
        focusedTrailingIconColor = palette.onCardSubtext,
        unfocusedTrailingIconColor = palette.onCardSubtext,
        cursorColor = palette.accent,
        errorBorderColor = palette.error,
        errorLabelColor = palette.error,
        errorTextColor = palette.onCard,
        errorSupportingTextColor = palette.error,
        errorCursorColor = palette.error,
        errorTrailingIconColor = palette.onCardSubtext
    )
}

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

private val RedColorScheme = darkColorScheme(
    primary = BarberRed,
    onPrimary = BoneWhite,
    primaryContainer = BarberRed,
    onPrimaryContainer = BoneWhite,
    secondary = BarberRed,
    onSecondary = BoneWhite,
    secondaryContainer = BoneWhiteSubtle,
    onSecondaryContainer = CharcoalBlack,
    tertiary = BoneWhiteMutedInk,
    onTertiary = BoneWhite,
    background = CharcoalBlack,
    onBackground = BoneWhite,
    surface = BoneWhite,
    onSurface = CharcoalBlack,
    surfaceVariant = BoneWhiteSubtle,
    onSurfaceVariant = BoneWhiteMutedInk,
    outline = BoneWhiteHairline,
    outlineVariant = CharcoalDivider,
    error = BarberRed,
    onError = BoneWhite
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
    themeMode: AppThemeMode = AppThemeMode.BLACK_AND_WHITE,
    content: @Composable () -> Unit
) {
    val palette = when (themeMode) {
        AppThemeMode.BLACK_AND_WHITE -> BlackAndWhiteAppPalette
        AppThemeMode.RED -> RedAppPalette
    }
    val colorScheme = when (themeMode) {
        AppThemeMode.BLACK_AND_WHITE -> MonochromeLightColorScheme
        AppThemeMode.RED -> RedColorScheme
    }
    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
