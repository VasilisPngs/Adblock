package com.vasilispngs.adblock.ui

import android.content.pm.PackageManager
import android.view.View
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.window.core.layout.WindowSizeClass
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.ui.theme.AdblockTheme
import com.vasilispngs.adblock.vpn.DohResult
import com.vasilispngs.adblock.vpn.TunnelState
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
class DnsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private var state by mutableStateOf(DnsUiState())
    private val switches = mutableListOf<Boolean>()
    private val urls = mutableListOf<String>()
    private var privateDnsOpened = 0
    private var vpnSettingsOpened = 0
    private val themes = mutableListOf<ThemeMode>()
    private var selfUpdate by mutableStateOf<SelfUpdate>(SelfUpdate.Idle)
    private var appUpdates = 0
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Test
    fun `shows the server and that DNS-over-HTTPS is off`() {
        show(DnsUiState(url = SERVER))

        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsOff()
        compose.onNodeWithText(string(R.string.tunnel_off)).assertIsDisplayed()
        compose.onNodeWithText(SERVER).assertIsDisplayed()
    }

    @Test
    fun `turns DNS-over-HTTPS on and off with its switch`() {
        show(DnsUiState(url = SERVER))
        compose.onNodeWithText(string(R.string.dns_over_https)).performClick()

        state = DnsUiState(url = SERVER, tunnel = TunnelState.On)
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsOn().assert(SwitchRole)
        compose.onNodeWithText(string(R.string.dns_over_https)).performClick()

        assertEquals(listOf(true, false), switches)
    }

    @Test
    fun `joins the home entries into one segmented group`() {
        show(DnsUiState(url = SERVER, privateDnsServer = "dns.google"))

        val dns = compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot()
        val url = compose.onNodeWithText(string(R.string.server_url)).getBoundsInRoot()
        assertEquals(ListItemDefaults.SegmentedGap, url.top - dns.bottom)
        assertEquals(true to false, corners(string(R.string.dns_over_https)))
        assertEquals(false to false, corners(string(R.string.server_url)))
        assertEquals(false to true, corners(string(R.string.private_dns_on, "dns.google")))
    }

    @Test
    fun `asks for a server before turning on without one`() {
        show(DnsUiState())

        compose.onNodeWithText(string(R.string.server_url_not_set)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.dns_over_https)).performClick()

        assertTrue(switches.isEmpty())
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun `saves only an https server URL`() {
        show(DnsUiState())
        compose.onNodeWithText(string(R.string.server_url)).performClick()

        compose.onNode(hasSetTextAction()).performTextReplacement("http://dns.example/dns-query")
        compose.onNodeWithText(string(R.string.server_url_invalid)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.save)).assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextReplacement(" https://dns.adguard-dns.com/dns-query ")
        compose.onNodeWithText(string(R.string.save)).assertIsEnabled().performClick()

        assertEquals(listOf("https://dns.adguard-dns.com/dns-query"), urls)
        compose.onAllNodesWithText(string(R.string.save)).assertCountEquals(0)
    }

    @Test
    fun `changes the server while DNS-over-HTTPS is on`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.On))
        compose.onNodeWithText(string(R.string.server_url)).assertIsEnabled().performClick()

        compose.onNode(hasSetTextAction()).performTextReplacement(OTHER)
        compose.onNodeWithText(string(R.string.save)).performClick()

        assertEquals(listOf(OTHER), urls)
        state = DnsUiState(url = SERVER, tunnel = TunnelState.AlwaysOn)
        compose.onNodeWithText(string(R.string.server_url)).assertIsEnabled()
    }

    @Test
    fun `keeps the server while DNS-over-HTTPS connects`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.Connecting))

        compose.onNodeWithText(string(R.string.server_url)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.tunnel_connecting)).assertIsDisplayed()
    }

    @Test
    fun `cancels a connection with its switch`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.Connecting))

        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsOn().performClick()

        assertEquals(listOf(false), switches)
    }

    @Test
    fun `keeps the list container on items that are disabled`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.Connecting, privateDnsServer = "dns.google"))

        val enabled = compose.onNodeWithText(string(R.string.private_dns_on, "dns.google")).captureToImage().asAndroidBitmap()
        val disabled = compose.onNodeWithText(string(R.string.server_url)).captureToImage().asAndroidBitmap()

        assertEquals(enabled.getPixel(enabled.width / 2, 2), disabled.getPixel(disabled.width / 2, 2))
    }

    @Test
    fun `leaves Always-on VPN to the system settings`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.AlwaysOn))

        compose.onNodeWithText(string(R.string.tunnel_always_on)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsOn().assert(SwitchRole).performClick()

        assertEquals(1, vpnSettingsOpened)
        assertTrue(switches.isEmpty())
    }

    @Test
    fun `warns that Block connections without VPN cuts apps off the internet`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.Lockdown, vpnSettings = true))

        compose.onNodeWithText(string(R.string.tunnel_always_on)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.always_on_vpn)).assertCountEquals(0)
        compose.onNodeWithText(string(R.string.lockdown_on)).assertIsDisplayed().performClick()

        assertEquals(1, vpnSettingsOpened)
    }

    @Test
    fun `looks the same on whether the app or Always-on VPN keeps it on`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.On))
        val app = compose.onNodeWithText(string(R.string.dns_over_https)).captureToImage().asAndroidBitmap()

        state = DnsUiState(url = SERVER, tunnel = TunnelState.AlwaysOn)
        val system = compose.onNodeWithText(string(R.string.dns_over_https)).captureToImage().asAndroidBitmap()

        assertEquals(app.getPixel(app.width / 2, 2), system.getPixel(system.width / 2, 2))
    }

    @Test
    fun `points to Always-on VPN below the server until it is on`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.On, vpnSettings = true))

        val url = compose.onNodeWithText(string(R.string.server_url)).getBoundsInRoot()
        val alwaysOn = compose.onNodeWithText(string(R.string.always_on_vpn)).getBoundsInRoot()
        assertTrue(alwaysOn.top > url.bottom)
        compose.onNodeWithText(string(R.string.always_on_vpn)).performClick()
        assertEquals(1, vpnSettingsOpened)

        state = DnsUiState(url = SERVER, tunnel = TunnelState.AlwaysOn, vpnSettings = true)
        compose.onAllNodesWithText(string(R.string.always_on_vpn)).assertCountEquals(0)
    }

    @Test
    fun `offers Always-on VPN only once the tunnel runs and Android lists Adblock`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.Off, vpnSettings = true))
        compose.onAllNodesWithText(string(R.string.always_on_vpn)).assertCountEquals(0)

        state = DnsUiState(url = SERVER, tunnel = TunnelState.Connecting, vpnSettings = true)
        compose.onAllNodesWithText(string(R.string.always_on_vpn)).assertCountEquals(0)

        state = DnsUiState(url = SERVER, tunnel = TunnelState.On, vpnSettings = true)
        compose.onNodeWithText(string(R.string.always_on_vpn)).assertIsDisplayed()
    }

    @Test
    fun `leaves out Always-on VPN where Android has no VPN settings`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.On, vpnSettings = false))

        compose.onAllNodesWithText(string(R.string.always_on_vpn)).assertCountEquals(0)
    }

    @Test
    fun `warns when Private DNS sends queries elsewhere`() {
        show(DnsUiState(url = SERVER, privateDnsServer = "dns.google"))

        compose.onNodeWithText(string(R.string.private_dns_on, "dns.google")).performClick()

        assertEquals(1, privateDnsOpened)
        state = DnsUiState(url = SERVER)
        compose.onAllNodesWithText(string(R.string.private_dns_on, "dns.google")).assertCountEquals(0)
    }

    @Test
    fun `warns below the server until it answers again`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.On, vpnSettings = true, serverFailing = true))

        val url = compose.onNodeWithText(string(R.string.server_url)).getBoundsInRoot()
        val failing = compose.onNodeWithText(string(R.string.server_not_answering)).getBoundsInRoot()
        val alwaysOn = compose.onNodeWithText(string(R.string.always_on_vpn)).getBoundsInRoot()
        assertEquals(ListItemDefaults.SegmentedGap, failing.top - url.bottom)
        assertEquals(ListItemDefaults.SegmentedGap, alwaysOn.top - failing.bottom)
        assertEquals(false to false, corners(string(R.string.server_not_answering)))
        assertEquals(false to true, corners(string(R.string.always_on_vpn)))

        state = DnsUiState(url = SERVER, tunnel = TunnelState.On, vpnSettings = true)
        compose.onAllNodesWithText(string(R.string.server_not_answering)).assertCountEquals(0)

        state = DnsUiState(url = SERVER, tunnel = TunnelState.On, vpnSettings = true, serverFailing = true)
        compose.onNodeWithText(string(R.string.server_not_answering)).performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun `explains why a server could not be used`() {
        assertEquals("The server answered with HTTP 403", DohResult.Rejected(403).text(context.resources))
        assertEquals(string(R.string.server_unreachable), DohResult.Failed.text(context.resources))
        assertEquals(string(R.string.server_unreachable), DohResult.Unreachable.text(context.resources))
    }

    @Test
    fun `focuses DNS-over-HTTPS first with a remote and keeps it there while it connects`() {
        show(DnsUiState(url = SERVER), keyboard = true)
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsFocused()

        state = DnsUiState(url = SERVER, tunnel = TunnelState.Connecting)
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsFocused()

        state = DnsUiState(url = SERVER, tunnel = TunnelState.On)
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `keeps compact margins and the title on the content edge on phones`() {
        assertContentEdge(16.dp)
        assertEquals(16.dp, compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot().left)
    }

    @Test
    @Config(qualifiers = "w620dp-h1000dp")
    fun `widens the margins from the medium breakpoint`() {
        assertContentEdge(24.dp)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-television")
    fun `centers the content inside the television overscan as wide as a compact window`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        show(DnsUiState(url = SERVER))

        val width = compose.onRoot().getBoundsInRoot().right
        val item = compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot()
        assertEquals(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp, item.right - item.left)
        assertEquals(item.left - 48.dp, width - 48.dp - item.right)
        assertEquals(item.left, text(string(R.string.home)).left)
    }

    @Test
    fun `keeps DNS-over-HTTPS on Home and the appearance in Settings`() {
        show(DnsUiState(url = SERVER))
        compose.onAllNodesWithText(string(R.string.theme)).assertCountEquals(0)

        openSettings()
        compose.onNodeWithText(string(R.string.theme)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.dns_over_https)).assertCountEquals(0)

        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.theme)).assertCountEquals(0)
    }

    @Test
    fun `slides Settings in from the end edge`() {
        show(DnsUiState(url = SERVER))
        val home = compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot()
        openSettings()
        val settings = compose.onNodeWithText(string(R.string.theme)).getBoundsInRoot()
        compose.onNodeWithContentDescription(string(R.string.back)).performClick()
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        openSettings()
        compose.mainClock.advanceTimeBy(TRANSITION_PEEK_MILLIS)
        val homeShift = home.left - compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot().left
        val settingsShift = compose.onNodeWithText(string(R.string.theme)).getBoundsInRoot().left - settings.left
        assertTrue(homeShift > 0.dp && homeShift < ForwardSlide)
        assertTrue(settingsShift > 0.dp && settingsShift < ForwardSlide)
    }

    @Test
    fun `follows the back gesture from Settings with Google's full screen motion`() {
        show(DnsUiState(url = SERVER))
        val home = compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot()
        openSettings()
        val settings = compose.onNodeWithText(string(R.string.theme)).getBoundsInRoot()

        backGesture(0.1f)
        val exit = PredictiveBack.transform(0.1f)
        assertScaled(settings, 1f - 0.1f * exit, compose.onNodeWithText(string(R.string.theme)).getBoundsInRoot())
        assertScaled(home, 1.1f - 0.1f * exit, compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot())

        compose.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
        compose.onAllNodesWithText(string(R.string.dns_over_https)).assertCountEquals(0)
        assertScaled(settings, 1f, compose.onNodeWithText(string(R.string.theme)).getBoundsInRoot())

        backGesture(0.6f)
        compose.runOnIdle { backDispatcher.onBackPressed() }
        assertScaled(home, 1f, compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot())
        compose.onAllNodesWithText(string(R.string.theme)).assertCountEquals(0)
    }

    @Test
    fun `blurs and dims behind dialogs through the dialog properties`() {
        show(DnsUiState(url = SERVER))

        openSettings()
        compose.onNodeWithText(string(R.string.theme)).performClick()

        val dialog = compose.onNode(isDialog()).fetchSemanticsNode().root as View
        val attributes = (dialog.parent as DialogWindowProvider).window.attributes
        val blur = context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        assertTrue(attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0)
        assertEquals(with(Density(context)) { 10.dp.roundToPx() }, attributes.blurBehindRadius)
        assertEquals(if (blur) 0.2f else 0.32f, attributes.dimAmount, 0f)
    }

    @Test
    fun `changes the theme from settings`() {
        show(DnsUiState(url = SERVER))

        openSettings()
        compose.onNodeWithText(string(R.string.theme_system)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.theme)).performClick()
        compose.onNodeWithText(string(R.string.theme_dark)).performClick()

        assertEquals(listOf(ThemeMode.Dark), themes)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `shows nothing about app updates in settings while the app is up to date`() {
        show(DnsUiState(url = SERVER))

        openSettings()

        compose.onNodeWithText(string(R.string.appearance)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.self_update_available)).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `updates the app from the first row of settings when a new version is available`() {
        selfUpdate = SelfUpdate.Available
        show(DnsUiState(url = SERVER))

        openSettings()
        val update = compose.onNodeWithText(string(R.string.self_update_available)).getBoundsInRoot()
        val heading = text(string(R.string.appearance))
        assertTrue(update.bottom < heading.top)
        assertEquals(true to true, corners(string(R.string.self_update_available)))
        compose.onNodeWithText(string(R.string.self_update_install)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.self_update_available)).performClick()

        assertEquals(1, appUpdates)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `shows the download of the app update without starting it again`() {
        selfUpdate = SelfUpdate.Downloading(0.42f)
        show(DnsUiState(url = SERVER))

        openSettings()
        compose.onNodeWithText(string(R.string.self_update_downloading, 42)).assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.42f, 0f..1f))).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.self_update_available)).performClick()

        assertEquals(0, appUpdates)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-television")
    fun `reaches the app update with the d-pad on television`() {
        selfUpdate = SelfUpdate.Available
        show(DnsUiState(url = SERVER), keyboard = true)
        openSettings()
        compose.onNodeWithText(string(R.string.theme)).requestFocus()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText(string(R.string.self_update_available)).assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText(string(R.string.theme)).assertIsFocused()
    }

    @Test
    fun `opens Settings from the top app bar and returns with its back arrow`() {
        show(DnsUiState(url = SERVER))

        openSettings()
        compose.onNodeWithText(string(R.string.theme)).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.back)).performClick()

        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.theme)).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `measures the settings by Google's list and app bar tokens`() {
        show(DnsUiState(url = SERVER))
        openSettings()
        val heading = text(string(R.string.appearance))
        val themeItem = compose.onNodeWithText(string(R.string.theme)).getBoundsInRoot()
        val themeTitle = text(string(R.string.theme))
        val title = text(string(R.string.settings))

        assertEquals(56.dp, title.left)
        assertEquals(themeTitle.left, heading.left)
        assertEquals(themeItem.left + 16.dp, themeTitle.left)
        assertEquals(72.dp, themeItem.bottom - themeItem.top)
        assertEquals(true to true, corners(string(R.string.theme)))

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

    private fun backGesture(progress: Float) {
        compose.runOnIdle {
            backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            backDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
    }

    private fun assertScaled(rest: DpRect, scale: Float, actual: DpRect) {
        assertEquals((rest.right - rest.left).value * scale, (actual.right - actual.left).value, 0.5f)
        assertEquals(((rest.left + rest.right) / 2).value, ((actual.left + actual.right) / 2).value, 0.5f)
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription(string(R.string.settings)).performClick()
    }

    private fun assertContentEdge(margin: Dp) {
        show(DnsUiState(url = SERVER))

        val width = compose.onRoot().getBoundsInRoot().right
        val item = compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot()
        val trailing = compose.onNodeWithContentDescription(string(R.string.settings), useUnmergedTree = true).getBoundsInRoot()
        val title = text(string(R.string.home))
        assertEquals(width - margin, item.right)
        assertEquals(width - margin, trailing.right)
        assertEquals(item.left, title.left)
    }

    private fun show(initial: DnsUiState, keyboard: Boolean = false) {
        state = initial
        compose.setContent {
            if (keyboard) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
            }
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            AdblockTheme {
                DnsScreen(
                    uiState = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onEnabledChange = { switches += it },
                    onUrlChange = { urls += it },
                    onPrivateDns = { privateDnsOpened++ },
                    onVpnSettings = { vpnSettingsOpened++ },
                    themeMode = ThemeMode.System,
                    onThemeChange = { themes += it },
                    selfUpdate = selfUpdate,
                    onUpdateApp = { appUpdates++ }
                )
            }
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val CORNER_INSET = 3
        const val SERVER = "https://adblock.example.workers.dev/dns-query/0123abcd"
        const val OTHER = "https://dns.adguard-dns.com/dns-query"
        const val TRANSITION_PEEK_MILLIS = 50L
        val ForwardSlide = 96.dp
        val SwitchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        val PredictiveBack = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
    }
}
