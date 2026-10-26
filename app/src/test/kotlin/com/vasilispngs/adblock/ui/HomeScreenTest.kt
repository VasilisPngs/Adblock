package com.vasilispngs.adblock.ui

import android.os.Looper
import androidx.annotation.StringRes
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.LoggedQuery
import com.vasilispngs.adblock.data.Origin
import com.vasilispngs.adblock.ui.theme.AdblockTheme
import com.vasilispngs.adblock.vpn.TunnelState
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
@Config(qualifiers = "w411dp-h891dp")
class HomeScreenTest {
    private val compose = createComposeRule()

    @get:Rule
    val composeRule = compose.drainingMainLooper()

    private val context = RuntimeEnvironment.getApplication()
    private var state by mutableStateOf(HomeUiState(loaded = true))
    private val switches = mutableListOf<Boolean>()
    private val filters = mutableListOf<LogFilter>()
    private val searches = mutableListOf<String>()
    private val rules = mutableListOf<Pair<String, Action>>()
    private val removed = mutableListOf<String>()
    private var privateDnsOpened = 0
    private var vpnSettingsOpened = 0

    @Test
    fun `turns protection on and off with its switch`() {
        show(HomeUiState(loaded = true))
        compose.onNodeWithText(string(R.string.tunnel_off)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.protection)).assertIsOff().assert(SwitchRole).performClick()

        state = HomeUiState(tunnel = TunnelState.On, loaded = true)
        compose.onNodeWithText(string(R.string.protection)).assertIsOn().performClick()

        assertEquals(listOf(true, false), switches)
    }

    @Test
    fun `cancels a connection with its switch`() {
        show(HomeUiState(tunnel = TunnelState.Connecting, loaded = true))

        compose.onNodeWithText(string(R.string.tunnel_connecting)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.protection)).assertIsOn().performClick()

        assertEquals(listOf(false), switches)
    }

    @Test
    fun `joins the status entries into one segmented group`() {
        show(HomeUiState(privateDnsServer = "dns.google", loaded = true))

        val protection = compose.onNodeWithText(string(R.string.protection)).getBoundsInRoot()
        val privateDns = compose.onNodeWithText(string(R.string.private_dns_on, "dns.google")).getBoundsInRoot()
        assertEquals(ListItemDefaults.SegmentedGap, privateDns.top - protection.bottom)
        assertEquals(true to false, corners(string(R.string.protection)))
        assertEquals(false to true, corners(string(R.string.private_dns_on, "dns.google")))
    }

    @Test
    fun `leaves Always-on VPN to the system settings`() {
        show(HomeUiState(tunnel = TunnelState.AlwaysOn, loaded = true))

        compose.onNodeWithText(string(R.string.tunnel_always_on)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.protection)).assertIsOn().assert(SwitchRole).performClick()

        assertEquals(1, vpnSettingsOpened)
        assertTrue(switches.isEmpty())
    }

    @Test
    fun `warns that Block connections without VPN cuts apps off the internet`() {
        show(HomeUiState(tunnel = TunnelState.Lockdown, vpnSettings = true, loaded = true))

        compose.onAllNodesWithText(string(R.string.always_on_vpn)).assertCountEquals(0)
        compose.onNodeWithText(string(R.string.lockdown_on)).assertIsDisplayed().performClick()

        assertEquals(1, vpnSettingsOpened)
    }

    @Test
    fun `offers Always-on VPN only once protection runs and Android lists Adblock`() {
        show(HomeUiState(vpnSettings = true, loaded = true))
        compose.onAllNodesWithText(string(R.string.always_on_vpn)).assertCountEquals(0)

        state = HomeUiState(tunnel = TunnelState.On, vpnSettings = false, loaded = true)
        compose.onAllNodesWithText(string(R.string.always_on_vpn)).assertCountEquals(0)

        state = HomeUiState(tunnel = TunnelState.On, vpnSettings = true, loaded = true)
        compose.onNodeWithText(string(R.string.always_on_vpn)).performClick()
        assertEquals(1, vpnSettingsOpened)
    }

    @Test
    fun `warns when Private DNS sends queries past Adblock`() {
        show(HomeUiState(privateDnsServer = "dns.google", loaded = true))

        compose.onNodeWithText(string(R.string.private_dns_on, "dns.google")).performClick()

        assertEquals(1, privateDnsOpened)
    }

    @Test
    fun `shows each query with its type and why it was blocked or allowed`() {
        show(
            HomeUiState(loaded = true),
            listOf(
                entry(1, "x.ads.example", Action.Block, Origin.List, "ads.example"),
                entry(2, "www.site.test", Action.Allow, Origin.Cache, null)
            )
        )

        compose.onNodeWithText("x.ads.example").assertIsDisplayed()
        compose.onNode(hasText("A · ${string(R.string.origin_list)} · ads.example", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("AAAA · ${string(R.string.origin_cache)}", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `allows a blocked name, blocks an allowed one and removes a rule from the log`() {
        show(
            HomeUiState(rules = mapOf("ruled.test" to Action.Allow), loaded = true),
            listOf(
                entry(1, "ads.example", Action.Block, Origin.List, "ads.example"),
                entry(2, "www.site.test", Action.Allow, Origin.None, null),
                entry(3, "ruled.test", Action.Allow, Origin.Allow, "ruled.test")
            )
        )

        compose.onNodeWithContentDescription(string(R.string.action_on_host, string(R.string.allow), "ads.example")).performClick()
        compose.onNodeWithContentDescription(string(R.string.action_on_host, string(R.string.block), "www.site.test")).performClick()
        compose.onNodeWithContentDescription(string(R.string.action_on_host, string(R.string.remove), "ruled.test")).performClick()
        compose.onNode(hasText(string(R.string.rule_meta, string(R.string.allow)), substring = true)).assertIsDisplayed()

        assertEquals(listOf("ads.example" to Action.Allow, "www.site.test" to Action.Block), rules)
        assertEquals(listOf("ruled.test"), removed)
    }

    @Test
    fun `keeps the log actions one width so the names line up`() {
        show(
            HomeUiState(rules = mapOf("ruled.test" to Action.Block), loaded = true),
            listOf(entry(1, "ads.example", Action.Block, Origin.List, "ads.example"), entry(2, "ruled.test", Action.Block, Origin.Custom, "ruled.test"))
        )

        val allow = compose.onNodeWithContentDescription(string(R.string.action_on_host, string(R.string.allow), "ads.example")).getBoundsInRoot()
        val remove = compose.onNodeWithContentDescription(string(R.string.action_on_host, string(R.string.remove), "ruled.test")).getBoundsInRoot()
        assertEquals(allow.right - allow.left, remove.right - remove.left)
        assertEquals(allow.right, remove.right)
    }

    @Test
    fun `filters the log with chips`() {
        show(HomeUiState(filter = LogFilter.Blocked, loaded = true))

        compose.onNodeWithText(string(R.string.filter_blocked)).assertIsSelected()
        compose.onNodeWithText(string(R.string.filter_allowed)).performClick()

        assertEquals(listOf(LogFilter.Allowed), filters)
    }

    @Test
    fun `searches the log as it is typed`() {
        show(HomeUiState(loaded = true))

        compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("ads")
        compose.runOnIdle { assertEquals("ads", searches.last()) }

        compose.onAllNodes(hasSetTextAction()).onLast().performImeAction()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNode(hasSetTextAction()).assert(hasText("ads"))
        settle()
    }

    @Test
    fun `says why the log is empty`() {
        show(HomeUiState(loaded = true))
        compose.awaitCondition { compose.onAllNodesWithText(string(R.string.no_queries)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(string(R.string.no_queries)).assertIsDisplayed()

        state = HomeUiState(logEnabled = false, loaded = true)
        compose.onNodeWithText(string(R.string.log_off)).assertIsDisplayed()

        state = HomeUiState(filter = LogFilter.Blocked, loaded = true)
        compose.onNodeWithText(string(R.string.no_results)).assertIsDisplayed()
    }

    @Test
    fun `keeps the search and filters on top while the log scrolls`() {
        show(HomeUiState(loaded = true), List(60) { entry(it.toLong(), "host$it.example", Action.Allow, Origin.None, null) })

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)

        compose.onNodeWithText(string(R.string.filter_all)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.protection)).assertCountEquals(0)
    }

    @Test
    fun `focuses protection first with a remote`() {
        show(HomeUiState(loaded = true), keyboard = true)

        compose.onNodeWithText(string(R.string.protection)).assertIsFocused()
    }

    @Test
    fun `keeps the search closed when a remote moves onto it`() {
        show(HomeUiState(loaded = true), keyboard = true)

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `opens the search when OK is pressed on it with a remote`() {
        show(HomeUiState(loaded = true), keyboard = true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onAllNodes(hasSetTextAction()).onLast().assertIsFocused()
        closeSearch()
    }

    @Test
    fun `still opens the search when Down is pressed on it with a remote`() {
        show(HomeUiState(loaded = true), keyboard = true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        closeSearch()
    }

    private fun closeSearch() {
        compose.onAllNodes(hasSetTextAction()).onLast().performImeAction()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun entry(id: Long, name: String, action: Action, origin: Origin, rule: String?) =
        LoggedQuery(id = id, at = System.currentTimeMillis(), name = name, type = if (id % 2 == 0L) "AAAA" else "A", action = action, origin = origin, rule = rule)

    private fun corners(text: String): Pair<Boolean, Boolean> {
        val image = compose.onNodeWithText(text).captureToImage().asAndroidBitmap()
        val container = image.getPixel(CORNER_INSET, image.height / 2)
        return (image.getPixel(CORNER_INSET, CORNER_INSET) != container) to
            (image.getPixel(CORNER_INSET, image.height - 1 - CORNER_INSET) != container)
    }

    private fun show(initial: HomeUiState, entries: List<LoggedQuery> = emptyList(), keyboard: Boolean = false) {
        state = initial
        compose.setContent {
            if (keyboard) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
            }
            AdblockTheme {
                val log = remember { flowOf(PagingData.from(entries, Loaded)) }.collectAsLazyPagingItems()
                HomeScreen(
                    uiState = state,
                    log = log,
                    snackbarHostState = null,
                    onEnabledChange = { switches += it },
                    onPrivateDns = { privateDnsOpened++ },
                    onVpnSettings = { vpnSettingsOpened++ },
                    onFilterChange = { filters += it },
                    onSearchChange = { searches += it },
                    onRule = { host, action -> rules += host to action },
                    onRemoveRule = { removed += it }
                )
            }
        }
        entries.firstOrNull()?.let { first -> compose.awaitCondition { compose.onAllNodesWithText(first.name).fetchSemanticsNodes().isNotEmpty() } }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val CORNER_INSET = 3
        const val SETTLE_MILLIS = 2_000L
        val SwitchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        val Loaded = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    }
}
