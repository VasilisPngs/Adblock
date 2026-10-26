package com.vasilispngs.adblock.ui

import androidx.annotation.StringRes
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.Appearance
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.ui.theme.AdblockTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private var logEnabled by mutableStateOf(true)
    private var selfUpdate by mutableStateOf<SelfUpdate>(SelfUpdate.Idle)
    private val themes = mutableListOf<ThemeMode>()
    private val dynamicColors = mutableListOf<Boolean>()
    private val logSettings = mutableListOf<Boolean>()
    private var appUpdates = 0

    @Test
    fun `changes the theme`() {
        show()

        compose.onNodeWithText(string(R.string.theme_system)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.theme)).performClick()
        compose.onNodeWithText(string(R.string.theme_dark)).performClick()

        assertEquals(listOf(ThemeMode.Dark), themes)
    }

    @Test
    fun `turns dynamic color on`() {
        show()

        compose.onNodeWithText(string(R.string.dynamic_color)).assertIsOff().assert(SwitchRole).performClick()

        assertEquals(listOf(true), dynamicColors)
    }

    @Test
    fun `turns query logging off and says how long the log is kept`() {
        show()

        compose.onNodeWithText(string(R.string.log_queries_description)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.log_queries)).assertIsOn().assert(SwitchRole).performClick()

        assertEquals(listOf(false), logSettings)
    }

    @Test
    fun `shows nothing about app updates while the app is up to date`() {
        show()

        compose.onNodeWithText(string(R.string.appearance)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.self_update_available)).assertCountEquals(0)
    }

    @Test
    fun `updates the app from the first row when a new version is available`() {
        selfUpdate = SelfUpdate.Available
        show()

        val update = compose.onNodeWithText(string(R.string.self_update_available)).getBoundsInRoot()
        assertTrue(update.bottom < text(string(R.string.appearance)).top)
        assertEquals(true to true, corners(string(R.string.self_update_available)))
        compose.onNodeWithText(string(R.string.self_update_install)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.self_update_available)).performClick()

        assertEquals(1, appUpdates)
    }

    @Test
    fun `shows the download of the app update without starting it again`() {
        selfUpdate = SelfUpdate.Downloading(0.42f)
        show()

        compose.onNodeWithText(string(R.string.self_update_downloading, 42)).assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.42f, 0f..1f))).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.self_update_available)).performClick()

        assertEquals(0, appUpdates)
    }

    @Test
    fun `measures the settings by Google's list and app bar tokens`() {
        show()
        val heading = text(string(R.string.appearance))
        val themeItem = compose.onNodeWithText(string(R.string.theme)).getBoundsInRoot()
        val themeTitle = text(string(R.string.theme))
        val dynamicItem = compose.onNodeWithText(string(R.string.dynamic_color)).getBoundsInRoot()
        val title = text(string(R.string.settings))

        assertEquals(16.dp, themeItem.left)
        assertEquals(themeItem.left, title.left)
        assertEquals(themeTitle.left, heading.left)
        assertEquals(themeItem.left + 16.dp, themeTitle.left)
        assertEquals(72.dp, themeItem.bottom - themeItem.top)
        assertEquals(72.dp, dynamicItem.bottom - dynamicItem.top)
        assertEquals(ListItemDefaults.SegmentedGap, dynamicItem.top - themeItem.bottom)
        assertEquals(true to false, corners(string(R.string.theme)))
        assertEquals(false to true, corners(string(R.string.dynamic_color)))
        assertEquals(true to true, corners(string(R.string.log_queries)))

        compose.onNodeWithText(string(R.string.theme)).performClick()
        val light = compose.onNodeWithText(string(R.string.theme_light)).getBoundsInRoot()
        val dark = compose.onNodeWithText(string(R.string.theme_dark)).getBoundsInRoot()
        assertEquals(56.dp, light.bottom - light.top)
        assertEquals(light.bottom, dark.top)
    }

    private fun text(value: String): DpRect {
        val nodes = compose.onAllNodesWithText(value, useUnmergedTree = true)
        return List(nodes.fetchSemanticsNodes().size) { nodes[it].getBoundsInRoot() }.minBy { it.top }
    }

    private fun corners(text: String): Pair<Boolean, Boolean> {
        val image = compose.onNodeWithText(text).captureToImage().asAndroidBitmap()
        val container = image.getPixel(CORNER_INSET, image.height / 2)
        return (image.getPixel(CORNER_INSET, CORNER_INSET) != container) to
            (image.getPixel(CORNER_INSET, image.height - 1 - CORNER_INSET) != container)
    }

    private fun show() {
        compose.setContent {
            AdblockTheme {
                SettingsScreen(
                    appearance = Appearance(ThemeMode.System, dynamicColor = false),
                    logEnabled = logEnabled,
                    selfUpdate = selfUpdate,
                    snackbarHostState = null,
                    onThemeChange = { themes += it },
                    onDynamicColorChange = { dynamicColors += it },
                    onLogEnabledChange = { logSettings += it },
                    onUpdateApp = { appUpdates++ }
                )
            }
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val CORNER_INSET = 3
        val SwitchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    }
}
