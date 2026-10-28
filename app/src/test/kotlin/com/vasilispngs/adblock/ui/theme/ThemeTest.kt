package com.vasilispngs.adblock.ui.theme

import android.app.UiModeManager
import android.content.res.Configuration
import androidx.annotation.ColorRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.vasilispngs.adblock.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val uiModeManager = shadowOf(context.getSystemService(UiModeManager::class.java))

    @Test
    fun `uses the default light colors`() = assertTheme(dark = false, contrast = 0f, expected = LightDefaultColorScheme)

    @Test
    fun `uses the default dark colors`() = assertTheme(dark = true, contrast = 0f, expected = DarkDefaultColorScheme)

    @Test
    fun `uses the medium contrast light colors`() = assertTheme(dark = false, contrast = 0.5f, expected = LightMediumContrastColorScheme)

    @Test
    fun `uses the medium contrast dark colors`() = assertTheme(dark = true, contrast = 0.5f, expected = DarkMediumContrastColorScheme)

    @Test
    fun `uses the high contrast light colors`() = assertTheme(dark = false, contrast = 1f, expected = LightHighContrastColorScheme)

    @Test
    fun `uses the high contrast dark colors`() = assertTheme(dark = true, contrast = 1f, expected = DarkHighContrastColorScheme)

    @Test
    fun `follows the contrast setting while it is shown`() {
        uiModeManager.setContrast(0f)
        var scheme: ColorScheme? = null
        compose.setContent {
            AdblockTheme(darkTheme = true) {
                scheme = MaterialTheme.colorScheme
            }
        }
        compose.waitForIdle()
        assertEquals(DarkDefaultColorScheme.toString(), scheme.toString())

        uiModeManager.setContrast(1f)
        compose.waitForIdle()
        assertEquals(DarkHighContrastColorScheme.toString(), scheme.toString())

        uiModeManager.setContrast(0.5f)
        compose.waitForIdle()
        assertEquals(DarkMediumContrastColorScheme.toString(), scheme.toString())
    }

    private fun assertTheme(dark: Boolean, contrast: Float, expected: ColorScheme) {
        uiModeManager.setContrast(contrast)
        var scheme: ColorScheme? = null

        compose.setContent {
            AdblockTheme(darkTheme = dark) {
                scheme = MaterialTheme.colorScheme
            }
        }
        compose.waitForIdle()

        assertEquals(expected.toString(), scheme.toString())
        assertEquals(expected.surface.toArgb(), color(R.color.window_background, dark))
    }

    private fun color(@ColorRes id: Int, dark: Boolean): Int {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return context.createConfigurationContext(configuration).getColor(id)
    }
}
