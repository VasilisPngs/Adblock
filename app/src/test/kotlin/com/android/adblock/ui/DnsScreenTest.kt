package com.android.adblock.ui

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.adblock.R
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
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsOn()
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
    fun `keeps the server while DNS-over-HTTPS is on or connecting`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.On))
        compose.onNodeWithText(string(R.string.server_url)).assertIsNotEnabled()

        state = DnsUiState(url = SERVER, tunnel = TunnelState.Connecting)
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
        show(DnsUiState(url = SERVER, tunnel = TunnelState.On, privateDnsServer = "dns.google"))

        val enabled = compose.onNodeWithText(string(R.string.private_dns_on, "dns.google")).captureToImage().asAndroidBitmap()
        val disabled = compose.onNodeWithText(string(R.string.server_url)).captureToImage().asAndroidBitmap()

        assertEquals(enabled.getPixel(enabled.width / 2, 2), disabled.getPixel(disabled.width / 2, 2))
    }

    @Test
    fun `leaves Always-on VPN to the system settings`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.AlwaysOn))

        compose.onNodeWithText(string(R.string.tunnel_always_on)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.server_url)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsSelected().performClick()

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
        assertContentEdge(24.dp + 48.dp)
    }

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
                    onVpnSettings = { vpnSettingsOpened++ }
                )
            }
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val SERVER = "https://adblock.example.workers.dev/dns-query/0123abcd"
    }
}
