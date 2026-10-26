package com.vasilispngs.adblock.ui

import android.content.pm.PackageManager
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.window.core.layout.WindowSizeClass
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.Appearance
import com.vasilispngs.adblock.data.LoggedQuery
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.ui.theme.AdblockTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `switches between the three destinations and goes back to Home`() {
        show()
        compose.onNodeWithText(string(R.string.query_log)).assertIsDisplayed()
        compose.onNode(tab(R.string.home)).assertIsSelected()

        compose.onNode(tab(R.string.protection)).performClick()
        compose.onNodeWithText(string(R.string.blocklists)).assertIsDisplayed()
        compose.onNode(tab(R.string.protection)).assertIsSelected()
        compose.onAllNodesWithText(string(R.string.query_log)).assertCountEquals(0)

        compose.onNode(tab(R.string.settings)).performClick()
        compose.onNodeWithText(string(R.string.appearance)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.blocklists)).assertCountEquals(0)

        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText(string(R.string.query_log)).assertIsDisplayed()
        compose.onNode(tab(R.string.home)).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `puts the navigation bar at the bottom of a phone and keeps compact margins`() {
        show()
        val height = compose.onRoot().getBoundsInRoot().bottom
        val home = compose.onNode(tab(R.string.home)).getBoundsInRoot()
        val settings = compose.onNode(tab(R.string.settings)).getBoundsInRoot()
        assertTrue(home.top > height - 120.dp)
        assertEquals(home.top, settings.top)

        val item = findItem(R.string.protection)
        assertEquals(16.dp, item.left)
        assertEquals(item.left, text(string(R.string.home)).left)
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `moves to a navigation rail and wider margins from the medium breakpoint`() {
        show()
        val home = compose.onNode(tab(R.string.home)).getBoundsInRoot()
        val settings = compose.onNode(tab(R.string.settings)).getBoundsInRoot()
        assertTrue(home.right < 120.dp)
        assertEquals(home.left, settings.left)
        assertTrue(settings.top > home.top)

        val item = findItem(R.string.protection)
        val width = compose.onRoot().getBoundsInRoot().right
        assertEquals(width - 24.dp, item.right)
        assertEquals(item.left, text(string(R.string.home)).left)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-television")
    fun `keeps the content inside the television overscan as wide as a compact window`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        show()

        val width = compose.onRoot().getBoundsInRoot().right
        val item = findItem(R.string.protection)
        val rail = compose.onNode(tab(R.string.home)).getBoundsInRoot()
        assertEquals(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp, item.right - item.left)
        assertTrue(rail.left >= 48.dp)
        assertTrue(item.right <= width - 48.dp - 24.dp)
        assertEquals(item.left, text(string(R.string.home)).left)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `fades through to the next destination without sliding`() {
        show()
        val before = findItem(R.string.protection)

        compose.mainClock.autoAdvance = false
        compose.onNode(tab(R.string.settings)).performClick()
        compose.mainClock.advanceTimeBy(FADE_OUT_PEEK_MILLIS)
        val during = findItem(R.string.protection)

        assertEquals(((before.left + before.right) / 2).value, ((during.left + during.right) / 2).value, 0.5f)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(string(R.string.appearance)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `blurs and dims behind dialogs through the dialog properties`() {
        show()
        compose.onNode(tab(R.string.settings)).performClick()
        compose.onNodeWithText(string(R.string.theme)).performClick()

        val dialog = compose.onNode(isDialog()).fetchSemanticsNode().root as View
        val attributes = (dialog.parent as DialogWindowProvider).window.attributes
        val blur = context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        assertTrue(attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0)
        assertEquals(with(Density(context)) { 10.dp.roundToPx() }, attributes.blurBehindRadius)
        assertEquals(if (blur) 0.2f else 0.32f, attributes.dimAmount, 0f)
    }

    private fun tab(@StringRes label: Int) =
        hasText(string(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun findItem(@StringRes title: Int): DpRect =
        compose.onNode(hasText(string(title)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).getBoundsInRoot()

    private fun text(value: String): DpRect {
        val nodes = compose.onAllNodesWithText(value, useUnmergedTree = true)
        return List(nodes.fetchSemanticsNodes().size) { nodes[it].getBoundsInRoot() }.minBy { it.top }
    }

    private fun show() {
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            AdblockTheme {
                val log = remember { flowOf(PagingData.empty<LoggedQuery>()) }.collectAsLazyPagingItems()
                AdblockNavigation(
                    snackbarHostState = remember { SnackbarHostState() },
                    home = { snackbar ->
                        HomeScreen(HomeUiState(loaded = true), log, snackbar, {}, {}, {}, {}, {}, { _, _ -> }, {})
                    },
                    protection = { snackbar ->
                        ProtectionScreen(ProtectionUiState(resolver = RESOLVER, loaded = true), snackbar, { _, _ -> }, {}, { _, _ -> }, {}, {})
                    },
                    settings = { snackbar ->
                        SettingsScreen(Appearance(ThemeMode.System, dynamicColor = false), true, SelfUpdate.Idle, snackbar, {}, {}, {}, {})
                    }
                )
            }
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val RESOLVER = "https://cloudflare-dns.com/dns-query"
        const val FADE_OUT_PEEK_MILLIS = 45L
    }
}
