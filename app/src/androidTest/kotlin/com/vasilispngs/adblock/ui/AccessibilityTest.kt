package com.vasilispngs.adblock.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.DEFAULT_LIST_NAME
import com.vasilispngs.adblock.data.DEFAULT_LIST_URL
import com.vasilispngs.adblock.data.ListSource
import com.vasilispngs.adblock.data.LoggedQuery
import com.vasilispngs.adblock.data.Origin
import com.vasilispngs.adblock.data.Rule
import com.vasilispngs.adblock.data.Appearance
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.ui.theme.AdblockTheme
import com.vasilispngs.adblock.vpn.TunnelState
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule as JUnitRule
import org.junit.Test

class AccessibilityTest {
    @get:JUnitRule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var dark by mutableStateOf(false)
    private val now = System.currentTimeMillis()
    private val entries = listOf(
        LoggedQuery(1, now, "x.ads.example", "A", Action.Block, Origin.List, "ads.example"),
        LoggedQuery(2, now - 1, "www.site.test", "AAAA", Action.Allow, Origin.Cache, null),
        LoggedQuery(3, now - 2, "ruled.test", "HTTPS", Action.Allow, Origin.Allow, "ruled.test")
    )
    private val protection = ProtectionUiState(
        sources = listOf(
            ListSource(DEFAULT_LIST_URL, DEFAULT_LIST_NAME, 0, domains = 85515, updatedAt = now - 3_600_000L),
            ListSource("https://lists.example/new.txt", "", 1),
            ListSource("https://lists.example/broken.txt", "Broken", 2, failedAt = now)
        ),
        rules = listOf(Rule("ruled.test", Action.Allow, 1)),
        resolver = "https://cloudflare-dns.com/dns-query",
        loaded = true
    )

    @Before
    fun enableAccessibilityChecks() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun off() = check(HomeUiState(loaded = true))

    @Test
    fun withoutQueries() = check(HomeUiState(loaded = true), log = emptyList())

    @Test
    fun connecting() = check(HomeUiState(tunnel = TunnelState.Connecting, loaded = true))

    @Test
    fun on() = check(
        HomeUiState(tunnel = TunnelState.On, vpnSettings = true, privateDnsServer = PRIVATE_DNS, rules = mapOf("ruled.test" to Action.Allow), loaded = true)
    )

    @Test
    fun alwaysOn() = check(HomeUiState(tunnel = TunnelState.AlwaysOn, loaded = true))

    @Test
    fun lockdown() = check(HomeUiState(tunnel = TunnelState.Lockdown, vpnSettings = true, loaded = true))

    @Test
    fun protection() {
        show(HomeUiState(loaded = true), entries, SelfUpdate.Idle)

        compose.onNode(tab(R.string.protection)).performClick()
        checkLightAndDark()
        compose.onNodeWithText(string(R.string.add_blocklist)).performClick()
        checkLightAndDark()
    }

    @Test
    fun addRule() {
        show(HomeUiState(loaded = true), entries, SelfUpdate.Idle)

        compose.onNode(tab(R.string.protection)).performClick()
        compose.onNodeWithText(string(R.string.add_rule)).performClick()
        checkLightAndDark()
    }

    @Test
    fun settings() {
        show(HomeUiState(loaded = true), entries, SelfUpdate.Available)

        compose.onNode(tab(R.string.settings)).performClick()
        checkLightAndDark()
        compose.onNodeWithText(string(R.string.theme)).performClick()
        checkLightAndDark()
    }

    @Test
    fun appUpdateDownload() {
        show(HomeUiState(loaded = true), entries, SelfUpdate.Downloading(DOWNLOADED))

        compose.onNode(tab(R.string.settings)).performClick()
        checkLightAndDark()
    }

    private fun check(state: HomeUiState, log: List<LoggedQuery> = entries) {
        show(state, log, SelfUpdate.Idle)
        checkLightAndDark()
    }

    private fun checkLightAndDark() {
        dark = false
        compose.onRoot().tryPerformAccessibilityChecks()
        dark = true
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private fun show(home: HomeUiState, log: List<LoggedQuery>, selfUpdate: SelfUpdate) {
        compose.setContent {
            AdblockTheme(darkTheme = dark) {
                val items = remember { flowOf(PagingData.from(log, Loaded)) }.collectAsLazyPagingItems()
                AdblockNavigation(
                    snackbarHostState = remember { SnackbarHostState() },
                    home = { snackbar, list, bar ->
                        HomeScreen(home, items, snackbar, {}, {}, {}, {}, {}, { _, _ -> }, {}, listState = list, barState = bar)
                    },
                    protection = { snackbar, list, bar ->
                        ProtectionScreen(protection, snackbar, { _, _ -> }, {}, { _, _ -> }, {}, {}, listState = list, barState = bar)
                    },
                    settings = { snackbar, list, bar ->
                        SettingsScreen(
                            Appearance(ThemeMode.System, dynamicColor = false), true, selfUpdate, snackbar, {}, {}, {}, {},
                            listState = list,
                            barState = bar
                        )
                    }
                )
            }
        }
    }

    private fun tab(@StringRes label: Int) =
        hasText(string(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun string(@StringRes id: Int): String = compose.activity.getString(id)

    private companion object {
        const val PRIVATE_DNS = "dns.google"
        const val DOWNLOADED = 0.42f
        val Loaded = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    }
}
