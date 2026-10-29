package com.vasilispngs.adblock.vpn

import com.vasilispngs.adblock.data.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(DohResult.Rejected(503), tunnel.serverFailure.value)

        tunnel.report(DohResult.Answer(ByteArray(12)))
        assertNull(tunnel.serverFailure.value)

        tunnel.report(DohResult.Failed)
        assertEquals(DohResult.Failed, tunnel.serverFailure.value)
    }

    @Test
    fun `learns nothing about the server from a request that never reached it`() {
        tunnel.report(UNREACHABLE)
        assertNull(tunnel.serverFailure.value)

        tunnel.report(DohResult.Rejected(429))
        tunnel.report(UNREACHABLE)
        assertEquals(DohResult.Rejected(429), tunnel.serverFailure.value)
    }

    @Test
    fun `forgets the failure when it starts again or the server changes`() {
        tunnel.report(DohResult.Failed)
        tunnel.update(TunnelState.Connecting)
        assertNull(tunnel.serverFailure.value)

        tunnel.report(DohResult.Failed)
        tunnel.serverChanged()
        assertNull(tunnel.serverFailure.value)
    }

    private companion object {
        val UNREACHABLE = DohResult.Unreachable(ErrorCode.Named("ERROR_TIMED_OUT"))
    }
}
