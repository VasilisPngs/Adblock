package com.vasilispngs.adblock.data

import android.app.UiModeManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
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
class AppPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val uiModeManager = context.getSystemService(UiModeManager::class.java)

    @Test
    fun `starts with the system theme and without dynamic color`() = runTest {
        assertEquals(Appearance(ThemeMode.System, dynamicColor = false), preferences().appearance.first())
    }

    @Test
    fun `remembers the theme and dynamic color apart`() = runTest {
        val preferences = preferences()
        preferences.setDynamicColor(true)
        preferences.setThemeMode(ThemeMode.Dark)

        assertEquals(Appearance(ThemeMode.Dark, dynamicColor = true), preferences.appearance.first())

        preferences.setDynamicColor(false)
        assertEquals(Appearance(ThemeMode.Dark, dynamicColor = false), preferences.appearance.first())
    }

    @Test
    fun `applies the chosen theme`() = runTest {
        preferences().setThemeMode(ThemeMode.Dark)

        assertEquals(UiModeManager.MODE_NIGHT_YES, shadowOf(uiModeManager).applicationNightMode)
    }

    @Test
    fun `applies the stored theme again when the system no longer has it`() = runTest {
        val preferences = preferences()
        preferences.setThemeMode(ThemeMode.Light)
        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)

        assertEquals(ThemeMode.Light, preferences.restoreThemeMode())
        assertEquals(UiModeManager.MODE_NIGHT_NO, shadowOf(uiModeManager).applicationNightMode)
    }

    private fun TestScope.preferences() = AppPreferences(
        context,
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "app.preferences_pb") }
    )
}
