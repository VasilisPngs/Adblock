package com.vasilispngs.adblock.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.paging.testing.asSnapshot
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.AdblockDatabase
import com.vasilispngs.adblock.data.LoggedQuery
import com.vasilispngs.adblock.data.Origin
import com.vasilispngs.adblock.data.ProtectionPreferences
import com.vasilispngs.adblock.vpn.DnsTunnel
import com.vasilispngs.adblock.vpn.DnsVpnService
import com.vasilispngs.adblock.vpn.TunnelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val database = AdblockDatabase.create(context)
    private val privateDnsServer = MutableStateFlow<String?>(null)
    private val now = 1_800_000_000_000L
    private lateinit var tunnel: DnsTunnel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tunnel = DnsTunnel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `turns on at once and shows the connection until the service takes over`() = runTest {
        val viewModel = viewModel()

        viewModel.turnOn()
        val ready = viewModel.uiState.first { it.readyToConnect }
        assertEquals(TunnelState.Connecting, ready.tunnel)

        viewModel.connectRequestHandled()
        viewModel.connect()
        assertEquals(DnsVpnService::class.java.name, shadowOf(context).nextStartedService.component?.className)
        tunnel.update(TunnelState.Connecting)
        tunnel.update(TunnelState.Off)
        assertEquals(TunnelState.Off, viewModel.uiState.first { it.tunnel == TunnelState.Off }.tunnel)
    }

    @Test
    fun `turns off again when the VPN is not allowed`() = runTest {
        val viewModel = viewModel()
        viewModel.turnOn()
        viewModel.connectRequestHandled()

        viewModel.cancel()

        val state = viewModel.uiState.first { it.tunnel == TunnelState.Off }
        assertFalse(state.readyToConnect)
    }

    @Test
    fun `turning off stops the service and drops a pending connection`() = runTest {
        val viewModel = viewModel()
        viewModel.turnOn()

        viewModel.turnOff()

        val state = viewModel.uiState.first { !it.readyToConnect }
        assertEquals(TunnelState.Off, state.tunnel)
        assertEquals(DnsVpnService.ACTION_STOP, shadowOf(context).nextStartedService.action)
    }

    @Test
    fun `asks a running tunnel again whether Always-on VPN holds it`() = runTest {
        val viewModel = viewModel()
        viewModel.refresh()
        assertNull(shadowOf(context).nextStartedService)

        tunnel.update(TunnelState.Lockdown)
        viewModel.refresh()

        assertEquals(DnsVpnService::class.java.name, shadowOf(context).nextStartedService.component?.className)
    }

    @Test
    fun `follows the Private DNS server and whether Android has VPN settings`() = runTest {
        val preferences = preferences()
        val viewModel = viewModel(preferences)
        privateDnsServer.value = "dns.google"
        assertEquals("dns.google", viewModel.uiState.first { it.privateDnsServer != null }.privateDnsServer)
        privateDnsServer.value = null
        assertNull(viewModel.uiState.first { it.privateDnsServer == null }.privateDnsServer)
        assertFalse(viewModel.uiState.value.vpnSettings)

        val settings = ComponentName("com.android.settings", "com.android.settings.VpnSettings")
        shadowOf(context.packageManager).run {
            addActivityIfNotPresent(settings)
            addIntentFilterForActivity(settings, IntentFilter(Settings.ACTION_VPN_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        tunnel = DnsTunnel(context)
        assertTrue(viewModel(preferences).uiState.value.vpnSettings)
    }

    @Test
    fun `keeps the rules for each logged name and changes them from the log`() = runTest {
        val viewModel = viewModel()

        viewModel.setRule("ads.example", Action.Allow)
        viewModel.setRule("tracker.test", Action.Block)
        assertEquals(
            mapOf("ads.example" to Action.Allow, "tracker.test" to Action.Block),
            viewModel.uiState.await { it.rules.size == 2 }.rules
        )

        viewModel.removeRule("ads.example")
        assertEquals(mapOf("tracker.test" to Action.Block), viewModel.uiState.await { it.rules.size == 1 }.rules)
    }

    @Test
    fun `shows the log of the last day newest first, filtered and searched`() = runTest {
        database.queries().insert(
            listOf(
                entry("old.example", now - 86_400_001L, Action.Allow),
                entry("ads.example", now - 2, Action.Block),
                entry("www.example", now - 1, Action.Allow),
                entry("mail.test", now, Action.Allow)
            )
        )
        val viewModel = viewModel()

        assertEquals(listOf("mail.test", "www.example", "ads.example"), names(viewModel))
        viewModel.setFilter(LogFilter.Blocked)
        assertEquals(listOf("ads.example"), names(viewModel))
        viewModel.setFilter(LogFilter.Allowed)
        viewModel.setSearch("EXAMPLE")
        assertEquals(listOf("www.example"), names(viewModel))
        assertEquals(LogFilter.Allowed, viewModel.uiState.first { it.search == "EXAMPLE" }.filter)
    }

    @Test
    fun `follows the log setting`() = runTest {
        val preferences = preferences()
        val viewModel = viewModel(preferences)
        assertTrue(viewModel.uiState.first { it.loaded }.logEnabled)

        preferences.setLogEnabled(false)

        assertFalse(viewModel.uiState.first { !it.logEnabled }.logEnabled)
    }

    private fun entry(name: String, at: Long, action: Action) =
        LoggedQuery(at = at, name = name, type = "A", action = action, origin = if (action == Action.Block) Origin.List else Origin.None, rule = null)

    private suspend fun TestScope.names(viewModel: HomeViewModel): List<String> {
        advanceUntilIdle()
        return viewModel.log.asSnapshot().map(LoggedQuery::name)
    }

    private fun TestScope.preferences() =
        ProtectionPreferences(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "protection.preferences_pb") })

    private suspend fun TestScope.viewModel(preferences: ProtectionPreferences = preferences()): HomeViewModel {
        val viewModel = HomeViewModel(tunnel, preferences, privateDnsServer, database.rules(), database.queries()) { now }
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.loaded }
        return viewModel
    }

    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(WAIT_MILLIS) { first(predicate) } }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
