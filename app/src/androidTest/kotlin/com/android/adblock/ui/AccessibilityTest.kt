package com.android.adblock.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.android.adblock.R
import com.android.adblock.data.Appearance
import com.android.adblock.data.SelfUpdate
import com.android.adblock.data.ThemeMode
import com.android.adblock.ui.theme.AdblockTheme
import com.android.adblock.vpn.TunnelState
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var dark by mutableStateOf(false)

    @Before
    fun enableAccessibilityChecks() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun off() = check(DnsUiState(url = SERVER))

    @Test
    fun withoutServer() = check(DnsUiState())

    @Test
    fun connecting() = check(DnsUiState(url = SERVER, tunnel = TunnelState.Connecting))

    @Test
    fun on() = check(DnsUiState(url = SERVER, tunnel = TunnelState.On, vpnSettings = true, privateDnsServer = PRIVATE_DNS))

    @Test
    fun alwaysOn() = check(DnsUiState(url = SERVER, tunnel = TunnelState.AlwaysOn))

    @Test
    fun lockdown() = check(DnsUiState(url = SERVER, tunnel = TunnelState.Lockdown, vpnSettings = true))

    @Test
    fun serverUrl() {
        show(DnsUiState(url = SERVER), SelfUpdate.Idle)

        compose.onNodeWithText(string(R.string.server_url)).performClick()
        checkLightAndDark()
    }

    @Test
    fun settings() {
        show(DnsUiState(url = SERVER), SelfUpdate.Available)

        compose.onNodeWithText(string(R.string.settings)).performClick()
        checkLightAndDark()
        compose.onNodeWithText(string(R.string.theme)).performClick()
        checkLightAndDark()
    }

    @Test
    fun appUpdateDownload() {
        show(DnsUiState(url = SERVER), SelfUpdate.Downloading(DOWNLOADED))

        compose.onNodeWithText(string(R.string.settings)).performClick()
        checkLightAndDark()
    }

    private fun check(state: DnsUiState) {
        show(state, SelfUpdate.Idle)
        checkLightAndDark()
    }

    private fun checkLightAndDark() {
        dark = false
        compose.onRoot().tryPerformAccessibilityChecks()
        dark = true
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private fun show(state: DnsUiState, selfUpdate: SelfUpdate) {
        compose.setContent {
            AdblockTheme(darkTheme = dark) {
                DnsScreen(
                    uiState = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onEnabledChange = {},
                    onUrlChange = {},
                    onPrivateDns = {},
                    onVpnSettings = {},
                    appearance = Appearance(ThemeMode.System, dynamicColor = false),
                    onThemeChange = {},
                    onDynamicColorChange = {},
                    selfUpdate = selfUpdate,
                    onUpdateApp = {}
                )
            }
        }
    }

    private fun string(@StringRes id: Int): String = compose.activity.getString(id)

    private companion object {
        const val SERVER = "https://adblock.example.workers.dev/dns-query/0123abcd"
        const val PRIVATE_DNS = "dns.google"
        const val DOWNLOADED = 0.42f
    }
}
