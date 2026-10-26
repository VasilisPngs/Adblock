package com.vasilispngs.adblock.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vasilispngs.adblock.data.AppPreferences
import com.vasilispngs.adblock.data.Appearance
import com.vasilispngs.adblock.data.ProtectionPreferences
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.SelfUpdater
import com.vasilispngs.adblock.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `keeps the theme and dynamic color it is given`() = runTest {
        val viewModel = viewModel()
        assertEquals(Appearance(ThemeMode.System, dynamicColor = false), viewModel.appearance.first { it != null })

        viewModel.setThemeMode(ThemeMode.Light)
        viewModel.setDynamicColor(true)

        assertEquals(
            Appearance(ThemeMode.Light, dynamicColor = true),
            withTimeoutOrNull(WAIT_MILLIS) { viewModel.appearance.first { it?.dynamicColor == true && it.themeMode == ThemeMode.Light } }
        )
    }

    @Test
    fun `turns query logging off and on`() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.logEnabled.first())

        viewModel.setLogEnabled(false)
        assertFalse(viewModel.logEnabled.first { !it })

        viewModel.setLogEnabled(true)
        assertTrue(viewModel.logEnabled.first { it })
    }

    @Test
    fun `looks for an app update and stays idle offline`() = runTest {
        val viewModel = viewModel()
        assertEquals(SelfUpdate.Idle, viewModel.selfUpdate.value)
    }

    private suspend fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(
            AppPreferences(context, PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "app.preferences_pb") }),
            ProtectionPreferences(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "protection.preferences_pb") }),
            SelfUpdater(context, backgroundScope) { throw IOException("Offline") }
        )
        backgroundScope.launch { viewModel.appearance.collect {} }
        backgroundScope.launch { viewModel.logEnabled.collect {} }
        viewModel.appearance.first { it != null }
        return viewModel
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
