package com.android.adblock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

private val GoogleSans = GoogleFont("Google Sans")

private val AppTypography = Typography(
    fontFamily = FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { weight ->
            Font(googleFont = GoogleSans, weight = weight)
        }
    )
)

internal val LightDefaultColorScheme = lightColorScheme(
    primary = Color(0xFF495F8B),
    onPrimary = Color(0xFFF9F8FF),
    primaryContainer = Color(0xFFB3C9FC),
    onPrimaryContainer = Color(0xFF2B416C),
    inversePrimary = Color(0xFFB3C9FC),
    secondary = Color(0xFF575F72),
    onSecondary = Color(0xFFF9F8FF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF4A5264),
    tertiary = Color(0xFF695781),
    onTertiary = Color(0xFFFEF6FF),
    tertiaryContainer = Color(0xFFE4CEFF),
    onTertiaryContainer = Color(0xFF54436B),
    background = Color(0xFFFAF9FE),
    onBackground = Color(0xFF2F323A),
    surface = Color(0xFFFAF9FE),
    onSurface = Color(0xFF2F323A),
    surfaceVariant = Color(0xFFE0E2ED),
    onSurfaceVariant = Color(0xFF5C5F68),
    surfaceTint = Color(0xFF495F8B),
    inverseSurface = Color(0xFF0D0E12),
    inverseOnSurface = Color(0xFF9C9CA2),
    error = Color(0xFFA83836),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFA746F),
    onErrorContainer = Color(0xFF6E0A12),
    outline = Color(0xFF787A84),
    outlineVariant = Color(0xFFAFB1BC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF9FE),
    surfaceContainer = Color(0xFFEDEDF6),
    surfaceContainerHigh = Color(0xFFE7E7F1),
    surfaceContainerHighest = Color(0xFFE0E2ED),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD8D9E4),
    primaryFixed = Color(0xFFB3C9FC),
    primaryFixedDim = Color(0xFFA6BBED),
    onPrimaryFixed = Color(0xFF152D57),
    onPrimaryFixedVariant = Color(0xFF354B75),
    secondaryFixed = Color(0xFFDBE2F9),
    secondaryFixedDim = Color(0xFFCDD4EA),
    onSecondaryFixed = Color(0xFF383F51),
    onSecondaryFixedVariant = Color(0xFF545B6E),
    tertiaryFixed = Color(0xFFE4CEFF),
    tertiaryFixedDim = Color(0xFFD6C0F0),
    onTertiaryFixed = Color(0xFF403057),
    onTertiaryFixedVariant = Color(0xFF5E4D75)
)

internal val DarkDefaultColorScheme = darkColorScheme(
    primary = Color(0xFFB6C6EE),
    onPrimary = Color(0xFF304060),
    primaryContainer = Color(0xFF425274),
    onPrimaryContainer = Color(0xFFD9E3FF),
    inversePrimary = Color(0xFF4F5F81),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF384052),
    secondaryContainer = Color(0xFF343B4D),
    onSecondaryContainer = Color(0xFFB7BFD5),
    tertiary = Color(0xFFEFDFFF),
    onTertiary = Color(0xFF5D4C74),
    tertiaryContainer = Color(0xFFE4CEFF),
    onTertiaryContainer = Color(0xFF54436B),
    background = Color(0xFF0D0E12),
    onBackground = Color(0xFFE3E5F0),
    surface = Color(0xFF0D0E12),
    onSurface = Color(0xFFE3E5F0),
    surfaceVariant = Color(0xFF23262D),
    onSurfaceVariant = Color(0xFFA8ABB5),
    surfaceTint = Color(0xFFB6C6EE),
    inverseSurface = Color(0xFFFAF9FE),
    inverseOnSurface = Color(0xFF545559),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    outline = Color(0xFF73757E),
    outlineVariant = Color(0xFF454850),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF292C34),
    surfaceContainer = Color(0xFF171A1F),
    surfaceContainerHigh = Color(0xFF1D2026),
    surfaceContainerHighest = Color(0xFF23262D),
    surfaceContainerLow = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF0D0E12),
    primaryFixed = Color(0xFFC7D7FF),
    primaryFixedDim = Color(0xFFB9C9F0),
    onPrimaryFixed = Color(0xFF283858),
    onPrimaryFixedVariant = Color(0xFF445476),
    secondaryFixed = Color(0xFFDBE2F9),
    secondaryFixedDim = Color(0xFFCDD4EA),
    onSecondaryFixed = Color(0xFF383F51),
    onSecondaryFixedVariant = Color(0xFF545B6E),
    tertiaryFixed = Color(0xFFE4CEFF),
    tertiaryFixedDim = Color(0xFFD6C0F0),
    onTertiaryFixed = Color(0xFF403057),
    onTertiaryFixedVariant = Color(0xFF5E4D75)
)

@Composable
fun AdblockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = remember(context, darkTheme, dynamicColor) {
        when {
            dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
            dynamicColor -> dynamicLightColorScheme(context)
            darkTheme -> DarkDefaultColorScheme
            else -> LightDefaultColorScheme
        }
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        content = content
    )
}
