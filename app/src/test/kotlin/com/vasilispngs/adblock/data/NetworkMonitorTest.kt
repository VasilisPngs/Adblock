package com.vasilispngs.adblock.data

import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NetworkMonitorTest {
    private val connectivity = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)

    @Test
    fun `follows the Private DNS server of the default network`() = runTest {
        val servers = mutableListOf<String?>()
        val collector = launch { NetworkMonitor(connectivity).privateDnsServer.toList(servers) }
        runCurrent()
        val callback = shadowOf(connectivity).networkCallbacks.single()
        val network = ShadowNetwork.newInstance(NETWORK_ID)

        callback.onLinkPropertiesChanged(network, privateDns("dns.google"))
        callback.onLinkPropertiesChanged(network, privateDns("dns.google"))
        callback.onLost(network)
        runCurrent()
        collector.cancel()
        runCurrent()

        assertEquals(listOf(null, "dns.google", null), servers)
        assertTrue(shadowOf(connectivity).networkCallbacks.isEmpty())
    }

    private fun privateDns(server: String) = LinkProperties().apply {
        LinkProperties::class.java.getMethod("setPrivateDnsServerName", String::class.java).invoke(this, server)
    }

    private companion object {
        const val NETWORK_ID = 100
    }
}
