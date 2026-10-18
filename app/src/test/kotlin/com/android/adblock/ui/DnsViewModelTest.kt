package com.android.adblock.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.android.adblock.data.DnsPreferences
import com.android.adblock.vpn.DnsVpnService
import com.android.adblock.vpn.DohResult
import com.android.adblock.vpn.TunnelState
import com.android.adblock.vpn.addressQuery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
class DnsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val asked = mutableListOf<Pair<String, ByteArray>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        DnsVpnService.state.value = TunnelState.Off
        Dispatchers.resetMain()
    }

    @Test
    fun `shows the saved server once it is read`() = runTest {
        val preferences = preferences()
        preferences.setUrl(SERVER)
        val viewModel = DnsViewModel(context, { _, _ -> DohResult.Failed }, preferences)

        assertFalse(viewModel.uiState.value.loaded)
        runCurrent()
        assertEquals(SERVER, withTimeoutOrNull(EVENT_WAIT_MILLIS) { viewModel.uiState.first { it.loaded } }?.url)
    }

    @Test
    fun `keeps the server it is given while off`() = runTest {
        val preferences = preferences()
        viewModel(preferences, DohResult.Failed).setUrl(SERVER)
        runCurrent()

        assertEquals(SERVER, preferences.url())
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `checks a new server before the running tunnel switches to it`() = runTest {
        val preferences = preferences()
        val viewModel = viewModel(preferences, DohResult.Answer(ByteArray(12)), url = SERVER)
        DnsVpnService.state.value = TunnelState.On

        viewModel.setUrl(OTHER)
        runCurrent()

        assertEquals(OTHER, asked.single().first)
        assertArrayEquals(addressQuery("dns.adguard-dns.com"), asked.single().second)
        assertEquals(OTHER, preferences.url())
        assertEquals(OTHER, withTimeoutOrNull(EVENT_WAIT_MILLIS) { viewModel.uiState.first { it.url == OTHER } }?.url)
        assertEquals(DnsVpnService::class.java.name, shadowOf(context).nextStartedService.component?.className)
        assertEquals(TunnelState.On, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `keeps the old server and says why when the new one does not answer`() = runTest {
        val preferences = preferences()
        val viewModel = viewModel(preferences, DohResult.Rejected(404), url = SERVER)
        DnsVpnService.state.value = TunnelState.AlwaysOn

        viewModel.setUrl(OTHER)

        assertEquals(SERVER, preferences.url())
        assertEquals(DnsEvent.Failed(DohResult.Rejected(404)), viewModel.nextEvent())
        assertNull(shadowOf(context).nextStartedService)
        assertEquals(TunnelState.AlwaysOn, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `does not start a tunnel that turned off while the new server was checked`() = runTest {
        val answer = CompletableDeferred<DohResult>()
        val preferences = preferences()
        preferences.setUrl(SERVER)
        DnsVpnService.state.value = TunnelState.On
        val viewModel = loaded(DnsViewModel(context, { _, _ -> answer.await() }, preferences))
        viewModel.setUrl(OTHER)

        DnsVpnService.state.value = TunnelState.Off
        answer.complete(DohResult.Answer(ByteArray(12)))
        runCurrent()

        assertEquals(OTHER, preferences.url())
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun `switches to the server saved last`() = runTest {
        val answers = mutableListOf<CompletableDeferred<DohResult>>()
        val preferences = preferences()
        preferences.setUrl(SERVER)
        DnsVpnService.state.value = TunnelState.On
        val viewModel = loaded(DnsViewModel(context, { _, _ -> CompletableDeferred<DohResult>().also(answers::add).await() }, preferences))
        viewModel.setUrl(OTHER)
        viewModel.setUrl(THIRD)

        answers[1].complete(DohResult.Answer(ByteArray(12)))
        answers[0].complete(DohResult.Answer(ByteArray(12)))
        runCurrent()

        assertEquals(THIRD, preferences.url())
    }

    @Test
    fun `asks the server for its own address before connecting`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Answer(ByteArray(12)), url = SERVER)

        viewModel.turnOn()

        assertEquals(SERVER, asked.single().first)
        assertArrayEquals(addressQuery("dns.nextdns.io"), asked.single().second)
        assertEquals(DnsEvent.Connect, viewModel.nextEvent())
        assertEquals(TunnelState.Connecting, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `stays off and says why when the server does not answer`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Rejected(403), url = SERVER)

        viewModel.turnOn()

        assertEquals(DnsEvent.Failed(DohResult.Rejected(403)), viewModel.nextEvent())
        assertEquals(TunnelState.Off, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `does nothing without a server`() = runTest {
        viewModel(preferences(), DohResult.Answer(ByteArray(12))).turnOn()

        assertTrue(asked.isEmpty())
        assertEquals(TunnelState.Off, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `turns off again when the VPN is not allowed`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Answer(ByteArray(12)), url = SERVER)
        viewModel.turnOn()

        viewModel.cancel()

        assertEquals(TunnelState.Off, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `turning off while it connects cancels the connection`() = runTest {
        val answer = CompletableDeferred<DohResult>()
        val preferences = preferences()
        preferences.setUrl(SERVER)
        val viewModel = loaded(DnsViewModel(context, { _, _ -> answer.await() }, preferences))
        viewModel.turnOn()

        viewModel.turnOff()
        answer.complete(DohResult.Answer(ByteArray(12)))

        assertEquals(TunnelState.Off, DnsVpnService.tunnelState.value)
        assertNull(viewModel.nextEvent())
    }

    @Test
    fun `asks a running tunnel again whether Always-on VPN holds it`() = runTest {
        val viewModel = viewModel(preferences(), DohResult.Failed, url = SERVER)
        viewModel.refresh()
        assertNull(shadowOf(context).nextStartedService)

        DnsVpnService.state.value = TunnelState.AlwaysOn
        viewModel.refresh()

        assertEquals(DnsVpnService::class.java.name, shadowOf(context).nextStartedService.component?.className)
    }

    @Test
    fun `knows whether Android has VPN settings to open`() = runTest {
        val preferences = preferences()
        assertFalse(viewModel(preferences, DohResult.Failed).uiState.value.vpnSettings)

        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Settings.ACTION_VPN_SETTINGS),
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "com.android.settings"; name = "VpnSettings" } }
        )

        assertTrue(viewModel(preferences, DohResult.Failed).uiState.value.vpnSettings)
    }

    private suspend fun DnsViewModel.nextEvent(): DnsEvent? = withTimeoutOrNull(EVENT_WAIT_MILLIS) { events.first() }

    private fun TestScope.preferences() = DnsPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "dns.preferences_pb") }
    )

    private fun TestScope.loaded(viewModel: DnsViewModel): DnsViewModel = viewModel.also { runCurrent() }

    private suspend fun TestScope.viewModel(preferences: DnsPreferences, result: DohResult, url: String = ""): DnsViewModel {
        preferences.setUrl(url)
        return loaded(
            DnsViewModel(context, { server, query ->
                asked += server to query
                result
            }, preferences)
        )
    }

    private companion object {
        const val SERVER = "https://dns.nextdns.io/abc123"
        const val OTHER = "https://dns.adguard-dns.com/dns-query"
        const val THIRD = "https://cloudflare-dns.com/dns-query"
        const val EVENT_WAIT_MILLIS = 200L
    }
}
