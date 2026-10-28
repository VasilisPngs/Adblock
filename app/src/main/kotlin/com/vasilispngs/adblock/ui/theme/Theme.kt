package com.vasilispngs.adblock.ui.theme

import android.app.UiModeManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
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

private const val MEDIUM_CONTRAST = 1 / 3f
private const val HIGH_CONTRAST = 2 / 3f

internal val LightDefaultColorScheme = lightColorScheme(
    primary = Color(0xFF595E6C),
    onPrimary = Color(0xFFF7F7FF),
    primaryContainer = Color(0xFFDEE2F2),
    onPrimaryContainer = Color(0xFF4D525F),
    inversePrimary = Color(0xFFE9EDFE),
    secondary = Color(0xFF5D5F65),
    onSecondary = Color(0xFFF9F8FF),
    secondaryContainer = Color(0xFFE2E2E9),
    onSecondaryContainer = Color(0xFF505157),
    tertiary = Color(0xFF5F5C78),
    onTertiary = Color(0xFFFCF7FF),
    tertiaryContainer = Color(0xFFD8D3F4),
    onTertiaryContainer = Color(0xFF4A4863),
    background = Color(0xFFFCF8F9),
    onBackground = Color(0xFF323235),
    surface = Color(0xFFFCF8F9),
    onSurface = Color(0xFF323235),
    surfaceVariant = Color(0xFFE4E2E5),
    onSurfaceVariant = Color(0xFF5F5F61),
    surfaceTint = Color(0xFF595E6C),
    inverseSurface = Color(0xFF0E0E0F),
    inverseOnSurface = Color(0xFF9E9C9D),
    error = Color(0xFF9F403D),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFE8983),
    onErrorContainer = Color(0xFF752121),
    outline = Color(0xFF7B7A7D),
    outlineVariant = Color(0xFFB3B1B4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFCF8F9),
    surfaceContainer = Color(0xFFF0EDEF),
    surfaceContainerHigh = Color(0xFFEAE7EA),
    surfaceContainerHighest = Color(0xFFE4E2E5),
    surfaceContainerLow = Color(0xFFF6F3F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDBD9DD),
    primaryFixed = Color(0xFFDEE2F2),
    primaryFixedDim = Color(0xFFD0D4E4),
    onPrimaryFixed = Color(0xFF3A3F4C),
    onPrimaryFixedVariant = Color(0xFF565B69),
    secondaryFixed = Color(0xFFE2E2E9),
    secondaryFixedDim = Color(0xFFD4D4DB),
    onSecondaryFixed = Color(0xFF3E3F45),
    onSecondaryFixedVariant = Color(0xFF5A5B61),
    tertiaryFixed = Color(0xFFD8D3F4),
    tertiaryFixedDim = Color(0xFFCAC6E6),
    onTertiaryFixed = Color(0xFF37354F),
    onTertiaryFixedVariant = Color(0xFF54516D)
)

internal val DarkDefaultColorScheme = darkColorScheme(
    primary = Color(0xFFC2C6D6),
    onPrimary = Color(0xFF3B404D),
    primaryContainer = Color(0xFF424753),
    onPrimaryContainer = Color(0xFFCCD0E0),
    inversePrimary = Color(0xFF5A5F6C),
    secondary = Color(0xFF9D9DA4),
    onSecondary = Color(0xFF1E2025),
    secondaryContainer = Color(0xFF3A3B41),
    onSecondaryContainer = Color(0xFFBEBFC5),
    tertiary = Color(0xFFE7E2FF),
    onTertiary = Color(0xFF53506C),
    tertiaryContainer = Color(0xFFD8D3F4),
    onTertiaryContainer = Color(0xFF4A4863),
    background = Color(0xFF0E0E0F),
    onBackground = Color(0xFFE7E5E8),
    surface = Color(0xFF0E0E0F),
    onSurface = Color(0xFFE7E5E8),
    surfaceVariant = Color(0xFF252628),
    onSurfaceVariant = Color(0xFFACAAAD),
    surfaceTint = Color(0xFFC2C6D6),
    inverseSurface = Color(0xFFFCF8F9),
    inverseOnSurface = Color(0xFF565556),
    error = Color(0xFFEE7D77),
    onError = Color(0xFF490106),
    errorContainer = Color(0xFF7F2927),
    onErrorContainer = Color(0xFFFF9993),
    outline = Color(0xFF767578),
    outlineVariant = Color(0xFF48484A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2B2C2F),
    surfaceContainer = Color(0xFF19191B),
    surfaceContainerHigh = Color(0xFF1F1F21),
    surfaceContainerHighest = Color(0xFF252628),
    surfaceContainerLow = Color(0xFF131314),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF0E0E0F),
    primaryFixed = Color(0xFFDEE2F2),
    primaryFixedDim = Color(0xFFD0D4E4),
    onPrimaryFixed = Color(0xFF3A3F4C),
    onPrimaryFixedVariant = Color(0xFF565B69),
    secondaryFixed = Color(0xFFE2E2E9),
    secondaryFixedDim = Color(0xFFD4D4DB),
    onSecondaryFixed = Color(0xFF3E3F45),
    onSecondaryFixedVariant = Color(0xFF5A5B61),
    tertiaryFixed = Color(0xFFD8D3F4),
    tertiaryFixedDim = Color(0xFFCAC6E6),
    onTertiaryFixed = Color(0xFF37354F),
    onTertiaryFixedVariant = Color(0xFF54516D)
)

internal val LightMediumContrastColorScheme = lightColorScheme(
    primary = Color(0xFF3E434F),
    onPrimary = Color(0xFFD5D9E9),
    primaryContainer = Color(0xFF6F7482),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFE9EDFE),
    secondary = Color(0xFF414348),
    onSecondary = Color(0xFFD9D9E0),
    secondaryContainer = Color(0xFF73747A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF42405A),
    onTertiary = Color(0xFFDBD6F7),
    tertiaryContainer = Color(0xFF75718E),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFCF8F9),
    onBackground = Color(0xFF232426),
    surface = Color(0xFFFCF8F9),
    onSurface = Color(0xFF232426),
    surfaceVariant = Color(0xFFE4E2E5),
    onSurfaceVariant = Color(0xFF434345),
    surfaceTint = Color(0xFF3E434F),
    inverseSurface = Color(0xFF0E0E0F),
    inverseOnSurface = Color(0xFFC7C4C5),
    error = Color(0xFF7A2524),
    onError = Color(0xFFFFCECA),
    errorContainer = Color(0xFFBB5551),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF5F5F61),
    outlineVariant = Color(0xFF7B7A7D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFCF8F9),
    surfaceContainer = Color(0xFFF0EDEF),
    surfaceContainerHigh = Color(0xFFEAE7EA),
    surfaceContainerHighest = Color(0xFFE4E2E5),
    surfaceContainerLow = Color(0xFFF6F3F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDBD9DD),
    primaryFixed = Color(0xFF6F7482),
    primaryFixedDim = Color(0xFF636876),
    onPrimaryFixed = Color(0xFFFFFFFF),
    onPrimaryFixedVariant = Color(0xFFFFFFFF),
    secondaryFixed = Color(0xFF73747A),
    secondaryFixedDim = Color(0xFF67686E),
    onSecondaryFixed = Color(0xFFFFFFFF),
    onSecondaryFixedVariant = Color(0xFFFFFFFF),
    tertiaryFixed = Color(0xFF75718E),
    tertiaryFixedDim = Color(0xFF686581),
    onTertiaryFixed = Color(0xFFFFFFFF),
    onTertiaryFixedVariant = Color(0xFFFFFFFF)
)

internal val DarkMediumContrastColorScheme = darkColorScheme(
    primary = Color(0xFFC2C6D6),
    onPrimary = Color(0xFF313642),
    primaryContainer = Color(0xFF6F7482),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF505562),
    secondary = Color(0xFFB8B8BF),
    onSecondary = Color(0xFF2A2C31),
    secondaryContainer = Color(0xFF73747A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFE7E2FF),
    onTertiary = Color(0xFF494761),
    tertiaryContainer = Color(0xFFD8D3F4),
    onTertiaryContainer = Color(0xFF413E58),
    background = Color(0xFF0E0E0F),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0E0E0F),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF252628),
    onSurfaceVariant = Color(0xFFBAB8BB),
    surfaceTint = Color(0xFFC2C6D6),
    inverseSurface = Color(0xFFFCF8F9),
    inverseOnSurface = Color(0xFF383839),
    error = Color(0xFFFF9F99),
    onError = Color(0xFF5B0D10),
    errorContainer = Color(0xFFBB5551),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF949396),
    outlineVariant = Color(0xFF767578),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2B2C2F),
    surfaceContainer = Color(0xFF19191B),
    surfaceContainerHigh = Color(0xFF1F1F21),
    surfaceContainerHighest = Color(0xFF252628),
    surfaceContainerLow = Color(0xFF131314),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF0E0E0F),
    primaryFixed = Color(0xFFDEE2F2),
    primaryFixedDim = Color(0xFFD0D4E4),
    onPrimaryFixed = Color(0xFF1A1F2B),
    onPrimaryFixedVariant = Color(0xFF3A3F4C),
    secondaryFixed = Color(0xFFE2E2E9),
    secondaryFixedDim = Color(0xFFD4D4DB),
    onSecondaryFixed = Color(0xFF1E1F24),
    onSecondaryFixedVariant = Color(0xFF3E3F45),
    tertiaryFixed = Color(0xFFD8D3F4),
    tertiaryFixedDim = Color(0xFFCAC6E6),
    onTertiaryFixed = Color(0xFF141229),
    onTertiaryFixedVariant = Color(0xFF37354F)
)

internal val LightHighContrastColorScheme = lightColorScheme(
    primary = Color(0xFF1F242F),
    onPrimary = Color(0xFFD5DAEA),
    primaryContainer = Color(0xFF4D525F),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFE9EDFE),
    secondary = Color(0xFF222429),
    onSecondary = Color(0xFFD9D9E0),
    secondaryContainer = Color(0xFF505258),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF23213A),
    onTertiary = Color(0xFFDBD6F7),
    tertiaryContainer = Color(0xFF524F6A),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFCF8F9),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFCF8F9),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE4E2E5),
    onSurfaceVariant = Color(0xFF232426),
    surfaceTint = Color(0xFF1F242F),
    inverseSurface = Color(0xFF0E0E0F),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFF4F0409),
    onError = Color(0xFFFFCFCB),
    errorContainer = Color(0xFF8E3432),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF434345),
    outlineVariant = Color(0xFF525254),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFCF8F9),
    surfaceContainer = Color(0xFFF0EDEF),
    surfaceContainerHigh = Color(0xFFEAE7EA),
    surfaceContainerHighest = Color(0xFFE4E2E5),
    surfaceContainerLow = Color(0xFFF6F3F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDBD9DD),
    primaryFixed = Color(0xFF4D525F),
    primaryFixedDim = Color(0xFF414653),
    onPrimaryFixed = Color(0xFFFFFFFF),
    onPrimaryFixedVariant = Color(0xFFFFFFFF),
    secondaryFixed = Color(0xFF505258),
    secondaryFixedDim = Color(0xFF45464C),
    onSecondaryFixed = Color(0xFFFFFFFF),
    onSecondaryFixedVariant = Color(0xFFFFFFFF),
    tertiaryFixed = Color(0xFF524F6A),
    tertiaryFixedDim = Color(0xFF46435E),
    onTertiaryFixed = Color(0xFFFFFFFF),
    onTertiaryFixedVariant = Color(0xFFFFFFFF)
)

internal val DarkHighContrastColorScheme = darkColorScheme(
    primary = Color(0xFFE1E5F5),
    onPrimary = Color(0xFF272C38),
    primaryContainer = Color(0xFF9FA3B2),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFF333844),
    secondary = Color(0xFFE5E5EC),
    onSecondary = Color(0xFF2A2C31),
    secondaryContainer = Color(0xFFA3A3AA),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFFE7E2FF),
    onTertiary = Color(0xFF2B2942),
    tertiaryContainer = Color(0xFFD8D3F4),
    onTertiaryContainer = Color(0xFF211F37),
    background = Color(0xFF0E0E0F),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0E0E0F),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF252628),
    onSurfaceVariant = Color(0xFFE7E5E8),
    surfaceTint = Color(0xFFE1E5F5),
    inverseSurface = Color(0xFFFCF8F9),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFFDEDB),
    onError = Color(0xFF5A0D10),
    errorContainer = Color(0xFFF5827C),
    onErrorContainer = Color(0xFF000000),
    outline = Color(0xFFBAB8BB),
    outlineVariant = Color(0xFFA4A3A6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2B2C2F),
    surfaceContainer = Color(0xFF19191B),
    surfaceContainerHigh = Color(0xFF1F1F21),
    surfaceContainerHighest = Color(0xFF252628),
    surfaceContainerLow = Color(0xFF131314),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF0E0E0F),
    primaryFixed = Color(0xFFDEE2F2),
    primaryFixedDim = Color(0xFFD0D4E4),
    onPrimaryFixed = Color(0xFF000000),
    onPrimaryFixedVariant = Color(0xFF1A1F2B),
    secondaryFixed = Color(0xFFE2E2E9),
    secondaryFixedDim = Color(0xFFD4D4DB),
    onSecondaryFixed = Color(0xFF000000),
    onSecondaryFixedVariant = Color(0xFF1E1F24),
    tertiaryFixed = Color(0xFFD8D3F4),
    tertiaryFixedDim = Color(0xFFCAC6E6),
    onTertiaryFixed = Color(0xFF000000),
    onTertiaryFixedVariant = Color(0xFF141229)
)

@Composable
fun AdblockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val contrast = systemContrast()
    val colorScheme = when {
        contrast >= HIGH_CONTRAST -> if (darkTheme) DarkHighContrastColorScheme else LightHighContrastColorScheme
        contrast >= MEDIUM_CONTRAST -> if (darkTheme) DarkMediumContrastColorScheme else LightMediumContrastColorScheme
        else -> if (darkTheme) DarkDefaultColorScheme else LightDefaultColorScheme
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        content = content
    )
}

@Composable
private fun systemContrast(): Float {
    val context = LocalContext.current
    val uiModeManager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    return produceState(uiModeManager.contrast, uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { value = it }
        uiModeManager.addContrastChangeListener(context.mainExecutor, listener)
        value = uiModeManager.contrast
        awaitDispose { uiModeManager.removeContrastChangeListener(listener) }
    }.value
}
