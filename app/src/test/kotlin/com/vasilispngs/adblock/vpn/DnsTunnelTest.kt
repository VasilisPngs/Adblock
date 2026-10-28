package com.vasilispngs.adblock.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DnsTunnelTest {
    private val tunnel = DnsTunnel(RuntimeEnvironment.getApplication())

    @Test
    fun `fails while the server rejects queries or sends no DNS answer`() {
        tunnel.report(DohResult.Rejected(503))
        assertTrue(tunnel.serverFailing.value)

        tunnel.report(DohResult.Answer(ByteArray(12)))
        assertFalse(tunnel.serverFailing.value)

        tunnel.report(DohResult.Failed)
        assertTrue(tunnel.serverFailing.value)
    }

    @Test
    fun `learns nothing about the server from a request that never reached it`() {
        tunnel.report(DohResult.Unreachable)
        assertFalse(tunnel.serverFailing.value)

        tunnel.report(DohResult.Rejected(429))
        tunnel.report(DohResult.Unreachable)
        assertTrue(tunnel.serverFailing.value)
    }

    @Test
    fun `forgets the failure when it starts again or the server changes`() {
        tunnel.report(DohResult.Failed)
        tunnel.update(TunnelState.Connecting)
        assertFalse(tunnel.serverFailing.value)

        tunnel.report(DohResult.Failed)
        tunnel.serverChanged()
        assertFalse(tunnel.serverFailing.value)
    }
}
