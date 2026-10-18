package com.android.adblock.ui.theme

import android.app.UiModeManager
import android.content.res.Configuration
import androidx.annotation.ColorRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.android.adblock.R
import com.android.adblock.data.AppPreferences
import com.android.adblock.data.Appearance
import com.android.adblock.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val uiModeManager = context.getSystemService(UiModeManager::class.java)

    @Test
    fun `follows the system with the default colors`() = assertTheme(ThemeMode.System, dynamicColor = false)

    @Test
    fun `follows the system with dynamic color`() = assertTheme(ThemeMode.System, dynamicColor = true)

    @Test
    fun `stays light with the default colors`() = assertTheme(ThemeMode.Light, dynamicColor = false)

    @Test
    fun `stays light with dynamic color`() = assertTheme(ThemeMode.Light, dynamicColor = true)

    @Test
    fun `stays dark with the default colors`() = assertTheme(ThemeMode.Dark, dynamicColor = false)

    @Test
    fun `stays dark with dynamic color`() = assertTheme(ThemeMode.Dark, dynamicColor = true)

    @Test
    fun `uses the blue scheme generated from the web apps seed`() {
        assertEquals(0xFF495F8B.toInt(), LightDefaultColorScheme.primary.toArgb())
        assertEquals(0xFFB6C6EE.toInt(), DarkDefaultColorScheme.primary.toArgb())
    }

    private fun assertTheme(mode: ThemeMode, dynamicColor: Boolean) {
        lateinit var stored: Appearance
        runTest {
            val preferences = AppPreferences(
                context,
                PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "app.preferences_pb") }
            )
            preferences.setThemeMode(mode)
            preferences.setDynamicColor(dynamicColor)
            stored = preferences.appearance.first()
        }
        assertEquals(Appearance(mode, dynamicColor), stored)
        assertEquals(mode.nightMode, shadowOf(uiModeManager).applicationNightMode)
        val dark = mode == ThemeMode.Dark
        var scheme: ColorScheme? = null

        compose.setContent {
            AdblockTheme(darkTheme = dark, dynamicColor = stored.dynamicColor) {
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
