package com.android.adblock.ui

import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.android.adblock.R
import com.android.adblock.data.Appearance
import com.android.adblock.data.ThemeMode
import com.android.adblock.ui.theme.AdblockTheme
import com.android.adblock.vpn.DohResult
import com.android.adblock.vpn.TunnelState
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
    private val dynamicColors = mutableListOf<Boolean>()

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
    fun `explains why a server could not be used`() {
        assertEquals("The server answered with HTTP 403", DohResult.Rejected(403).text(context.resources))
        assertEquals(string(R.string.server_unreachable), DohResult.Failed.text(context.resources))
    }

    @Test
    fun `focuses DNS-over-HTTPS first with a remote or keyboard`() {
        show(DnsUiState(url = SERVER), keyboard = true)

        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsFocused()
    }

    @Test
    fun `keeps the remote focus on DNS-over-HTTPS while it connects`() {
        show(DnsUiState(url = SERVER), keyboard = true)

        state = DnsUiState(url = SERVER, tunnel = TunnelState.Connecting)
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsFocused()

        state = DnsUiState(url = SERVER, tunnel = TunnelState.On)
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `keeps compact margins and the title on the content edge on phones`() {
        assertContentEdge(16.dp)
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `widens the margins from the medium breakpoint`() {
        assertContentEdge(24.dp)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-television")
    fun `keeps the content inside the television overscan`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        assertContentEdge(24.dp + 48.dp)
    }

    @Test
    fun `changes the theme from the list`() {
        show(DnsUiState(url = SERVER))

        compose.onNodeWithText(string(R.string.theme_system)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.theme)).performClick()
        compose.onNodeWithText(string(R.string.theme_dark)).performClick()

        assertEquals(listOf(ThemeMode.Dark), themes)
    }

    @Test
    fun `turns dynamic color on from the list`() {
        show(DnsUiState(url = SERVER))

        compose.onNodeWithText(string(R.string.dynamic_color)).performScrollTo().assertIsOff().assert(SwitchRole).performClick()

        assertEquals(listOf(true), dynamicColors)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `measures the settings by Google's list and app bar tokens`() {
        show(DnsUiState(url = SERVER))
        compose.onNodeWithText(string(R.string.dynamic_color)).performScrollTo()
        val heading = text(string(R.string.appearance))
        val themeItem = compose.onNodeWithText(string(R.string.theme)).getBoundsInRoot()
        val themeTitle = text(string(R.string.theme))
        val dynamicItem = compose.onNodeWithText(string(R.string.dynamic_color)).getBoundsInRoot()
        val title = text(string(R.string.app_name))

        assertEquals(16.dp, title.left)
        assertEquals(themeTitle.left, heading.left)
        assertEquals(themeItem.left + 16.dp, themeTitle.left)
        assertEquals(72.dp, themeItem.bottom - themeItem.top)
        assertEquals(72.dp, dynamicItem.bottom - dynamicItem.top)
        assertEquals(themeItem.left, dynamicItem.left)
        assertEquals(themeItem.right, dynamicItem.right)
        assertEquals(8.dp, dynamicItem.top - themeItem.bottom)

        compose.onNodeWithText(string(R.string.theme)).performClick()
        val light = compose.onNodeWithText(string(R.string.theme_light)).getBoundsInRoot()
        val dark = compose.onNodeWithText(string(R.string.theme_dark)).getBoundsInRoot()
        assertEquals(56.dp, light.bottom - light.top)
        assertEquals(light.bottom, dark.top)
    }

    private fun text(value: String): DpRect = compose.onNodeWithText(value, useUnmergedTree = true).getBoundsInRoot()

    private fun assertContentEdge(margin: Dp) {
        show(DnsUiState(url = SERVER))

        val width = compose.onRoot().getBoundsInRoot().right
        val item = compose.onNodeWithText(string(R.string.dns_over_https)).getBoundsInRoot()
        val title = compose.onNodeWithText(string(R.string.app_name), useUnmergedTree = true).getBoundsInRoot()
        assertEquals(margin, item.left)
        assertEquals(width - margin, item.right)
        assertEquals(item.left, title.left)
    }

    private fun show(initial: DnsUiState, keyboard: Boolean = false) {
        state = initial
        compose.setContent {
            if (keyboard) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
            }
            AdblockTheme {
                DnsScreen(
                    uiState = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onEnabledChange = { switches += it },
                    onUrlChange = { urls += it },
                    onPrivateDns = { privateDnsOpened++ },
                    onVpnSettings = { vpnSettingsOpened++ },
                    appearance = Appearance(ThemeMode.System, dynamicColor = false),
                    onThemeChange = { themes += it },
                    onDynamicColorChange = { dynamicColors += it }
                )
            }
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val SERVER = "https://adblock.example.workers.dev/dns-query/0123abcd"
        const val OTHER = "https://dns.adguard-dns.com/dns-query"
        val SwitchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    }
}
