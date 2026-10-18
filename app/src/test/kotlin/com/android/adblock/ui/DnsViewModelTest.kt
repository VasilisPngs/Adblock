package com.android.adblock.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.android.adblock.data.DnsPreferences
import com.android.adblock.vpn.DnsTunnel
import com.android.adblock.vpn.DnsVpnService
import com.android.adblock.vpn.DohResult
import com.android.adblock.vpn.TunnelState
import com.android.adblock.vpn.addressQuery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
class DnsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val asked = mutableListOf<Pair<String, ByteArray>>()
    private val privateDnsServer = MutableStateFlow<String?>(null)
    private lateinit var tunnel: DnsTunnel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tunnel = DnsTunnel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows the saved server once it is read`() = runTest {
        val preferences = preferences()
        preferences.setUrl(SERVER)
        val viewModel = DnsViewModel(preferences, tunnel, privateDnsServer) { _, _ -> DohResult.Failed }

        assertFalse(viewModel.uiState.value.loaded)
        assertEquals(SERVER, withTimeoutOrNull(WAIT_MILLIS) { viewModel.uiState.first { it.loaded } }?.url)
    }

    @Test
    fun `keeps the server it is given while off`() = runTest {
        val preferences = preferences()
        viewModel(preferences, DohResult.Failed).setUrl(SERVER)
        runCurrent()

        assertEquals(SERVER, preferences.url.first())
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `checks a new server before the running tunnel switches to it`() = runTest {
        val preferences = preferences()
        val viewModel = viewModel(preferences, DohResult.Answer(ByteArray(12)), url = SERVER)
        tunnel.update(TunnelState.On)

        viewModel.setUrl(OTHER)
        runCurrent()

        assertEquals(OTHER, asked.single().first)
        assertArrayEquals(addressQuery("dns.adguard-dns.com"), asked.single().second)
        assertEquals(OTHER, preferences.url.first())
        assertEquals(OTHER, withTimeoutOrNull(WAIT_MILLIS) { viewModel.uiState.first { it.url == OTHER } }?.url)
        assertNull(shadowOf(context).nextStartedService)
        assertEquals(TunnelState.On, viewModel.uiState.value.tunnel)
    }

    @Test
    fun `keeps the old server and says why when the new one does not answer`() = runTest {
        val preferences = preferences()
        val viewModel = viewModel(preferences, DohResult.Rejected(404), url = SERVER)
        tunnel.update(TunnelState.AlwaysOn)

        viewModel.setUrl(OTHER)

        assertEquals(SERVER, preferences.url.first())
        assertEquals(DohResult.Rejected(404), viewModel.failure())
        assertEquals(TunnelState.AlwaysOn, viewModel.uiState.value.tunnel)

        viewModel.failureShown()
        assertNull(viewModel.uiState.first { it.failure == null }.failure)
    }

    @Test
    fun `saves a checked server without starting a tunnel that turned off meanwhile`() = runTest {
        val answer = CompletableDeferred<DohResult>()
        val preferences = preferences()
        preferences.setUrl(SERVER)
        tunnel.update(TunnelState.On)
        val viewModel = loaded(DnsViewModel(preferences, tunnel, privateDnsServer) { _, _ -> answer.await() })
        viewModel.setUrl(OTHER)

        tunnel.update(TunnelState.Off)
        answer.complete(DohResult.Answer(ByteArray(12)))
        runCurrent()

        assertEquals(OTHER, preferences.url.first())
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun `switches to the server saved last`() = runTest {
        val answers = mutableListOf<CompletableDeferred<DohResult>>()
        val preferences = preferences()
        preferences.setUrl(SERVER)
        tunnel.update(TunnelState.On)
        val viewModel = loaded(DnsViewModel(preferences, tunnel, privateDnsServer) { _, _ -> CompletableDeferred<DohResult>().also(answers::add).await() })
        viewModel.setUrl(OTHER)
        viewModel.setUrl(THIRD)

        answers[1].complete(DohResult.Answer(ByteArray(12)))
        answers[0].complete(DohResult.Answer(ByteArray(12)))
        runCurrent()

        assertEquals(THIRD, preferences.url.first())
    }

    @Test
    fun `asks the server for its own address before connecting`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Answer(ByteArray(12)), url = SERVER)

        viewModel.turnOn()

        assertEquals(SERVER, asked.single().first)
        assertArrayEquals(addressQuery("dns.nextdns.io"), asked.single().second)
        val ready = withTimeoutOrNull(WAIT_MILLIS) { viewModel.uiState.first { it.readyToConnect } }
        assertEquals(TunnelState.Connecting, ready?.tunnel)
    }

    @Test
    fun `stays off and says why when the server does not answer`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Rejected(403), url = SERVER)

        viewModel.turnOn()

        assertEquals(DohResult.Rejected(403), viewModel.failure())
        assertEquals(TunnelState.Off, viewModel.uiState.value.tunnel)
        assertFalse(viewModel.uiState.value.readyToConnect)
    }

    @Test
    fun `does nothing without a server`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Answer(ByteArray(12)))

        viewModel.turnOn()

        assertTrue(asked.isEmpty())
        assertEquals(TunnelState.Off, viewModel.uiState.value.tunnel)
    }

    @Test
    fun `turns off again when the VPN is not allowed`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Answer(ByteArray(12)), url = SERVER)
        viewModel.turnOn()
        viewModel.uiState.first { it.readyToConnect }
        viewModel.connectRequestHandled()

        viewModel.cancel()

        val state = viewModel.uiState.first { it.tunnel == TunnelState.Off }
        assertFalse(state.readyToConnect)
    }

    @Test
    fun `shows the connection until the service takes over`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Answer(ByteArray(12)), url = SERVER)
        viewModel.turnOn()
        viewModel.uiState.first { it.readyToConnect }
        viewModel.connectRequestHandled()

        viewModel.connect()

        assertEquals(DnsVpnService::class.java.name, shadowOf(context).nextStartedService.component?.className)
        assertEquals(TunnelState.Connecting, viewModel.uiState.value.tunnel)
        tunnel.update(TunnelState.Connecting)
        tunnel.update(TunnelState.Off)
        assertEquals(TunnelState.Off, viewModel.uiState.first { it.tunnel == TunnelState.Off }.tunnel)
    }

    @Test
    fun `turning off while it connects cancels the connection`() = runTest {
        val answer = CompletableDeferred<DohResult>()
        val preferences = preferences()
        preferences.setUrl(SERVER)
        val viewModel = loaded(DnsViewModel(preferences, tunnel, privateDnsServer) { _, _ -> answer.await() })
        viewModel.turnOn()

        viewModel.turnOff()
        answer.complete(DohResult.Answer(ByteArray(12)))
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(TunnelState.Off, state.tunnel)
        assertFalse(state.readyToConnect)
        assertNull(state.failure)
    }

    @Test
    fun `asks a running tunnel again whether Always-on VPN holds it`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Failed, url = SERVER)
        viewModel.refresh()
        assertNull(shadowOf(context).nextStartedService)

        tunnel.update(TunnelState.AlwaysOn)
        viewModel.refresh()

        assertEquals(DnsVpnService::class.java.name, shadowOf(context).nextStartedService.component?.className)
    }

    @Test
    fun `follows the Private DNS server as it changes`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Failed, url = SERVER)

        privateDnsServer.value = "dns.google"
        assertEquals("dns.google", viewModel.uiState.first { it.privateDnsServer != null }.privateDnsServer)

        privateDnsServer.value = null
        assertNull(viewModel.uiState.first { it.privateDnsServer == null }.privateDnsServer)
    }

    @Test
    fun `knows whether Android has VPN settings to open`() = runTest {
        assertFalse(DnsTunnel(context).vpnSettings)

        val settings = ComponentName("com.android.settings", "com.android.settings.VpnSettings")
        shadowOf(context.packageManager).run {
            addActivityIfNotPresent(settings)
            addIntentFilterForActivity(settings, IntentFilter(Settings.ACTION_VPN_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        tunnel = DnsTunnel(context)

        assertTrue(viewModel(preferences(), DohResult.Failed).uiState.value.vpnSettings)
    }

    private suspend fun DnsViewModel.failure(): DohResult? =
        withTimeoutOrNull(WAIT_MILLIS) { uiState.first { it.failure != null } }?.failure

    private fun TestScope.preferences() = DnsPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "dns.preferences_pb") }
    )

    private suspend fun TestScope.loaded(viewModel: DnsViewModel): DnsViewModel {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.loaded }
        return viewModel
    }

    private suspend fun TestScope.viewModel(preferences: DnsPreferences, result: DohResult, url: String = ""): DnsViewModel {
        preferences.setUrl(url)
        return loaded(
            DnsViewModel(preferences, tunnel, privateDnsServer) { server, query ->
                asked += server to query
                result
            }
        )
    }

    private companion object {
        const val SERVER = "https://dns.nextdns.io/abc123"
        const val OTHER = "https://dns.adguard-dns.com/dns-query"
        const val THIRD = "https://cloudflare-dns.com/dns-query"
        const val WAIT_MILLIS = 200L
    }
}
