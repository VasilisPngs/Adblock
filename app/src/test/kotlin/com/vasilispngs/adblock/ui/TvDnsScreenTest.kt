package com.vasilispngs.adblock.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.ErrorCode
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.ui.theme.AdblockTvTheme
import com.vasilispngs.adblock.vpn.DohResult
import com.vasilispngs.adblock.vpn.TunnelState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television")
class TvDnsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private var state by mutableStateOf(DnsUiState())
    private val switches = mutableListOf<Boolean>()
    private val urls = mutableListOf<String>()
    private var vpnSettingsOpened = 0
    private val themes = mutableListOf<ThemeMode>()
    private var selfUpdate by mutableStateOf<SelfUpdate>(SelfUpdate.Idle)
    private var appUpdates = 0
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Test
    fun `focuses DNS-over-HTTPS first and turns it on and off with the remote`() {
        show(DnsUiState(url = SERVER))
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsFocused().assertIsOff()
        pressKey(Key.DirectionCenter)

        state = DnsUiState(url = SERVER, tunnel = TunnelState.On)
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsFocused().assertIsOn()
        pressKey(Key.DirectionCenter)

        assertEquals(listOf(true, false), switches)
    }

    @Test
    fun `asks for a server before turning on without one`() {
        show(DnsUiState())
        pressKey(Key.DirectionCenter)

        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        assertEquals(emptyList<Boolean>(), switches)
    }

    @Test
    fun `saves only an https server URL`() {
        show(DnsUiState(url = SERVER))
        select(compose.onNodeWithText(string(R.string.server_url)))

        compose.onNode(hasSetTextAction()).performTextReplacement("dns.example")
        compose.onNodeWithText(string(R.string.server_url_invalid)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.save)).assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextReplacement(OTHER)
        select(compose.onNodeWithText(string(R.string.save)).assertIsEnabled())

        assertEquals(listOf(OTHER), urls)
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsDisplayed()
    }

    @Test
    fun `leaves Always-on VPN to the system settings`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.AlwaysOn))
        pressKey(Key.DirectionCenter)

        assertEquals(1, vpnSettingsOpened)
        assertEquals(emptyList<Boolean>(), switches)
    }

    @Test
    fun `warns when the server is not answering`() {
        show(DnsUiState(url = SERVER, tunnel = TunnelState.On, serverFailure = DohResult.Unreachable(ErrorCode.Named("ERROR_TIMED_OUT"))))

        compose.onNodeWithText(string(R.string.server_not_answering_description)).assertIsDisplayed()
    }

    @Test
    fun `moves into the theme options on the chosen theme and changes it`() {
        show(DnsUiState(url = SERVER), themeMode = ThemeMode.Light)
        select(compose.onNodeWithContentDescription(string(R.string.settings)))
        compose.onNodeWithText(string(R.string.theme)).assertIsFocused()

        pressKey(Key.DirectionRight)
        compose.onNode(isFocused()).assert(hasText(string(R.string.theme_light)))
        pressKey(Key.DirectionDown)
        pressKey(Key.DirectionCenter)

        assertEquals(listOf(ThemeMode.Dark), themes)
    }

    @Test
    fun `goes back from the theme options to the list and then home`() {
        show(DnsUiState(url = SERVER))
        select(compose.onNodeWithContentDescription(string(R.string.settings)))
        pressKey(Key.DirectionRight)

        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText(string(R.string.theme)).assertIsFocused()

        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText(string(R.string.dns_over_https)).assertIsDisplayed()
    }

    @Test
    fun `updates the app from the settings list`() {
        selfUpdate = SelfUpdate.Available
        show(DnsUiState(url = SERVER))
        select(compose.onNodeWithContentDescription(string(R.string.settings)))
        compose.onNodeWithText(string(R.string.self_update_available)).assertIsFocused()

        pressKey(Key.DirectionCenter)

        assertEquals(1, appUpdates)
    }

    private fun select(node: SemanticsNodeInteraction) {
        node.requestFocus()
        pressKey(Key.DirectionCenter)
    }

    private fun pressKey(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
    }

    private fun show(initial: DnsUiState, themeMode: ThemeMode = ThemeMode.System) {
        state = initial
        compose.setContent {
            val inputModeManager = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            AdblockTvTheme {
                TvDnsScreen(
                    uiState = state,
                    onEnabledChange = { switches += it },
                    onUrlChange = { urls += it },
                    onPrivateDns = {},
                    onVpnSettings = { vpnSettingsOpened++ },
                    themeMode = themeMode,
                    onThemeChange = { themes += it },
                    selfUpdate = selfUpdate,
                    onUpdateApp = { appUpdates++ }
                )
            }
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val SERVER = "https://adblock.example.workers.dev/dns-query/0123abcd"
        const val OTHER = "https://dns.adguard-dns.com/dns-query"
    }
}
