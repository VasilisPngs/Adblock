package com.android.adblock.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import com.android.adblock.data.DnsPreferences
import com.android.adblock.vpn.DnsVpnService
import com.android.adblock.vpn.DohResult
import com.android.adblock.vpn.TunnelState
import com.android.adblock.vpn.addressQuery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DnsViewModelTest {
    private val context = RuntimeEnvironment.getApplication()
    private val asked = mutableListOf<Pair<String, ByteArray>>()

    @After
    fun tearDown() {
        DnsVpnService.state.value = TunnelState.Off
    }

    @Test
    fun `keeps the server it is given while off`() {
        viewModel(DohResult.Failed).setUrl(SERVER)

        assertEquals(SERVER, DnsPreferences(context).url)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `checks a new server before the running tunnel switches to it`() {
        val viewModel = viewModel(DohResult.Answer(ByteArray(12)), url = SERVER)
        DnsVpnService.state.value = TunnelState.On

        viewModel.setUrl(OTHER)

        assertEquals(OTHER, asked.single().first)
        assertArrayEquals(addressQuery("dns.adguard-dns.com"), asked.single().second)
        assertEquals(OTHER, DnsPreferences(context).url)
        assertEquals(OTHER, runBlocking { withTimeoutOrNull(EVENT_WAIT_MILLIS) { viewModel.uiState.first { it.url == OTHER } } }?.url)
        assertEquals(DnsVpnService::class.java.name, shadowOf(context).nextStartedService.component?.className)
        assertEquals(TunnelState.On, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `keeps the old server and says why when the new one does not answer`() {
        val viewModel = viewModel(DohResult.Rejected(404), url = SERVER)
        DnsVpnService.state.value = TunnelState.AlwaysOn

        viewModel.setUrl(OTHER)

        assertEquals(SERVER, DnsPreferences(context).url)
        assertEquals(DnsEvent.Failed(DohResult.Rejected(404)), viewModel.nextEvent())
        assertNull(shadowOf(context).nextStartedService)
        assertEquals(TunnelState.AlwaysOn, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `does not start a tunnel that turned off while the new server was checked`() {
        val answer = CompletableDeferred<DohResult>()
        DnsPreferences(context).url = SERVER
        DnsVpnService.state.value = TunnelState.On
        val viewModel = DnsViewModel(context) { _, _ -> answer.await() }
        viewModel.setUrl(OTHER)

        DnsVpnService.state.value = TunnelState.Off
        answer.complete(DohResult.Answer(ByteArray(12)))

        assertEquals(OTHER, DnsPreferences(context).url)
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun `switches to the server saved last`() {
        val answers = mutableListOf<CompletableDeferred<DohResult>>()
        DnsPreferences(context).url = SERVER
        DnsVpnService.state.value = TunnelState.On
        val viewModel = DnsViewModel(context) { _, _ -> CompletableDeferred<DohResult>().also(answers::add).await() }
        viewModel.setUrl(OTHER)
        viewModel.setUrl(THIRD)

        answers[1].complete(DohResult.Answer(ByteArray(12)))
        answers[0].complete(DohResult.Answer(ByteArray(12)))

        assertEquals(THIRD, DnsPreferences(context).url)
    }

    @Test
    fun `asks the server for its own address before connecting`() {
        val viewModel = viewModel(DohResult.Answer(ByteArray(12)), url = SERVER)

        viewModel.turnOn()

        assertEquals(SERVER, asked.single().first)
        assertArrayEquals(addressQuery("dns.nextdns.io"), asked.single().second)
        assertEquals(DnsEvent.Connect, viewModel.nextEvent())
        assertEquals(TunnelState.Connecting, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `stays off and says why when the server does not answer`() {
        val viewModel = viewModel(DohResult.Rejected(403), url = SERVER)

        viewModel.turnOn()

        assertEquals(DnsEvent.Failed(DohResult.Rejected(403)), viewModel.nextEvent())
        assertEquals(TunnelState.Off, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `does nothing without a server`() {
        viewModel(DohResult.Answer(ByteArray(12))).turnOn()

        assertTrue(asked.isEmpty())
        assertEquals(TunnelState.Off, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `turns off again when the VPN is not allowed`() {
        val viewModel = viewModel(DohResult.Answer(ByteArray(12)), url = SERVER)
        viewModel.turnOn()

        viewModel.cancel()

        assertEquals(TunnelState.Off, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `turning off while it connects cancels the connection`() {
        val answer = CompletableDeferred<DohResult>()
        DnsPreferences(context).url = SERVER
        val viewModel = DnsViewModel(context) { _, _ -> answer.await() }
        viewModel.turnOn()

        viewModel.turnOff()
        answer.complete(DohResult.Answer(ByteArray(12)))

        assertEquals(TunnelState.Off, DnsVpnService.tunnelState.value)
        assertNull(viewModel.nextEvent())
    }

    @Test
    fun `asks a running tunnel again whether Always-on VPN holds it`() {
        val viewModel = viewModel(DohResult.Failed, url = SERVER)
        viewModel.refresh()
        assertNull(shadowOf(context).nextStartedService)

        DnsVpnService.state.value = TunnelState.AlwaysOn
        viewModel.refresh()

        assertEquals(DnsVpnService::class.java.name, shadowOf(context).nextStartedService.component?.className)
    }

    @Test
    fun `knows whether Android has VPN settings to open`() {
        assertFalse(viewModel(DohResult.Failed).uiState.value.vpnSettings)

        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Settings.ACTION_VPN_SETTINGS),
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "com.android.settings"; name = "VpnSettings" } }
        )

        assertTrue(viewModel(DohResult.Failed).uiState.value.vpnSettings)
    }

    private fun DnsViewModel.nextEvent(): DnsEvent? = runBlocking { withTimeoutOrNull(EVENT_WAIT_MILLIS) { events.first() } }

    private fun viewModel(result: DohResult, url: String = ""): DnsViewModel {
        DnsPreferences(context).url = url
        return DnsViewModel(context) { server, query ->
            asked += server to query
            result
        }
    }

    private companion object {
        const val SERVER = "https://dns.nextdns.io/abc123"
        const val OTHER = "https://dns.adguard-dns.com/dns-query"
        const val THIRD = "https://cloudflare-dns.com/dns-query"
        const val EVENT_WAIT_MILLIS = 200L
    }
}
