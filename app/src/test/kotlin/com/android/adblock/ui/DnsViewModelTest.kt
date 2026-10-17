package com.android.adblock.ui

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DnsViewModelTest {
    private val context = RuntimeEnvironment.getApplication()
    private val asked = mutableListOf<Pair<String, ByteArray>>()

    @After
    fun tearDown() {
        DnsVpnService.state.value = TunnelState.Off
    }

    @Test
    fun `keeps the server it is given`() {
        viewModel(DohResult.Failed).setUrl(SERVER)

        assertEquals(SERVER, DnsPreferences(context).url)
    }

    @Test
    fun `asks the server for its own address before connecting`() {
        val viewModel = viewModel(DohResult.Answer(ByteArray(12)), url = SERVER)

        viewModel.turnOn()

        assertEquals(SERVER, asked.single().first)
        assertArrayEquals(addressQuery("dns.nextdns.io"), asked.single().second)
        assertEquals(DnsEvent.Connect, runBlocking { viewModel.events.first() })
        assertEquals(TunnelState.Connecting, DnsVpnService.tunnelState.value)
    }

    @Test
    fun `stays off and says why when the server does not answer`() {
        val viewModel = viewModel(DohResult.Rejected(403), url = SERVER)

        viewModel.turnOn()

        assertEquals(DnsEvent.Failed(DohResult.Rejected(403)), runBlocking { viewModel.events.first() })
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
        assertNull(runBlocking { withTimeoutOrNull(EVENT_WAIT_MILLIS) { viewModel.events.first() } })
    }

    private fun viewModel(result: DohResult, url: String = ""): DnsViewModel {
        DnsPreferences(context).url = url
        return DnsViewModel(context) { server, query ->
            asked += server to query
            result
        }
    }

    private companion object {
        const val SERVER = "https://dns.nextdns.io/abc123"
        const val EVENT_WAIT_MILLIS = 200L
    }
}
