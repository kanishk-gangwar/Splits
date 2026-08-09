package com.kanishk.splits.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------- palette --

private val Violet = Color(0xFF7C5CFF)
private val VioletDeep = Color(0xFF5B3CE0)
private val VioletSoft = Color(0xFFB9A6FF)
private val Mint = Color(0xFF31D0AA)
private val MintDeep = Color(0xFF0F9D77)
private val Coral = Color(0xFFFF6B6B)
private val CoralDeep = Color(0xFFD93B4E)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = VioletSoft,
    secondary = Mint,
    onSecondary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF14352E),
    onSecondaryContainer = Mint,
    error = Coral,
    onError = Color.White,
    errorContainer = Color(0xFF3B1B20),
    onErrorContainer = Color(0xFFFFB3B3),
    background = Color(0xFF0B0D14),
    onBackground = Color(0xFFE9EAF2),
    surface = Color(0xFF0B0D14),
    onSurface = Color(0xFFE9EAF2),
    surfaceVariant = Color(0xFF1A1D28),
    onSurfaceVariant = Color(0xFF9AA0B8),
    surfaceContainerLowest = Color(0xFF07080D),
    surfaceContainerLow = Color(0xFF11141C),
    surfaceContainer = Color(0xFF151824),
    surfaceContainerHigh = Color(0xFF1C2030),
    surfaceContainerHighest = Color(0xFF232839),
    outline = Color(0xFF2E3446),
    outlineVariant = Color(0xFF232839),
    inverseSurface = Color(0xFFE9EAF2),
    inverseOnSurface = Color(0xFF11141C),
)

private val LightColors = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE4FF),
    onPrimaryContainer = Color(0xFF2A1A70),
    secondary = MintDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4F5EA),
    onSecondaryContainer = Color(0xFF04432F),
    error = CoralDeep,
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E3),
    onErrorContainer = Color(0xFF6B0F1D),
    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF14161F),
    surface = Color(0xFFF6F6FB),
    onSurface = Color(0xFF14161F),
    surfaceVariant = Color(0xFFEDEDF5),
    onSurfaceVariant = Color(0xFF5F6478),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F1F8),
    surfaceContainerHighest = Color(0xFFE9E9F3),
    outline = Color(0xFFD5D6E2),
    outlineVariant = Color(0xFFE6E7EF),
    inverseSurface = Color(0xFF14161F),
    inverseOnSurface = Color(0xFFF6F6FB),
)

/**
 * Money has a direction, and Material's scheme has no slot for "this is good news".
 * These are carried alongside so the same green/red reads correctly in both themes.
 */
@Immutable
data class MoneyColors(
    val positive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val negativeContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

private val DarkMoney = MoneyColors(
    positive = Mint,
    positiveContainer = Color(0xFF12332C),
    negative = Coral,
    negativeContainer = Color(0xFF34191E),
    neutral = Color(0xFF9AA0B8),
    neutralContainer = Color(0xFF1C2030),
    heroStart = Color(0xFF6E4EF6),
    heroEnd = Color(0xFF9E5CFF),
)

private val LightMoney = MoneyColors(
    positive = MintDeep,
    positiveContainer = Color(0xFFD8F5EC),
    negative = CoralDeep,
    negativeContainer = Color(0xFFFFE2E5),
    neutral = Color(0xFF6B7086),
    neutralContainer = Color(0xFFEDEDF5),
    heroStart = Color(0xFF6448E8),
    heroEnd = Color(0xFF9A5CF0),
)

val LocalMoneyColors = staticCompositionLocalOf { DarkMoney }

/** Shorthand so screens can write `SplitsTheme.money.positive`. */
object SplitsTheme {
    val money: MoneyColors
        @Composable get() = LocalMoneyColors.current
}

// ---------------------------------------------------------------- typography --

private val SplitsTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    )
}

/** Amounts get tabular-ish treatment: bold, tight, never wrapping mid-number. */
val MoneyTextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.4).sp,
)

private val SplitsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

enum class ThemeMode { System, Light, Dark;
    companion object {
        fun fromPref(raw: String): ThemeMode = when (raw) {
            "light" -> Light
            "dark" -> Dark
            else -> System
        }
    }

    val pref: String get() = when (this) {
        System -> "system"
        Light -> "light"
        Dark -> "dark"
    }
}

@Composable
fun SplitsTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = SplitsTypography,
            shapes = SplitsShapes,
            content = content,
        )
    }
}
