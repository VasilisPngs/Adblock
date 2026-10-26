package com.vasilispngs.adblock.ui.theme

import android.content.res.Configuration
import androidx.annotation.ColorRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.vasilispngs.adblock.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `uses the default light colors`() = assertTheme(dark = false, dynamicColor = false)

    @Test
    fun `uses the dynamic light colors`() = assertTheme(dark = false, dynamicColor = true)

    @Test
    fun `uses the default dark colors`() = assertTheme(dark = true, dynamicColor = false)

    @Test
    fun `uses the dynamic dark colors`() = assertTheme(dark = true, dynamicColor = true)

    private fun assertTheme(dark: Boolean, dynamicColor: Boolean) {
        var scheme: ColorScheme? = null

        compose.setContent {
            AdblockTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                scheme = MaterialTheme.colorScheme
            }
        }
        compose.waitForIdle()

        val expected = when {
            dynamicColor && dark -> dynamicDarkColorScheme(context)
            dynamicColor -> dynamicLightColorScheme(context)
            dark -> DarkDefaultColorScheme
            else -> LightDefaultColorScheme
        }
        assertEquals(expected.toString(), scheme.toString())
        val window = if (dynamicColor) R.color.dynamic_window_background else R.color.window_background
        assertEquals(expected.surface.toArgb(), color(window, dark))
    }

    private fun color(@ColorRes id: Int, dark: Boolean): Int {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return context.createConfigurationContext(configuration).getColor(id)
    }
}
