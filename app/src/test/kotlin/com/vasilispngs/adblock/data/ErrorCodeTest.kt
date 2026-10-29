package com.vasilispngs.adblock.data

import android.net.http.NetworkException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
class ErrorCodeTest {
    @Test
    fun `names the HTTP status of a failed request`() {
        assertEquals(ErrorCode.Http(404), HttpStatusException(404).errorCode())
    }

    @Test
    fun `names the network error HttpEngine reports`() {
        assertEquals(ErrorCode.Named("ERROR_HOSTNAME_NOT_RESOLVED"), network(NetworkException.ERROR_HOSTNAME_NOT_RESOLVED).errorCode())
        assertEquals(ErrorCode.Named("ERROR_QUIC_PROTOCOL_FAILED"), network(NetworkException.ERROR_QUIC_PROTOCOL_FAILED).errorCode())
    }

    @Test
    fun `names a platform exception`() {
        assertEquals(ErrorCode.Named("SocketTimeoutException"), SocketTimeoutException().errorCode())
    }

    @Test
    fun `names the platform exception that a renamed exception extends`() {
        assertEquals(ErrorCode.Named("IOException"), Renamed().errorCode())
    }

    private fun network(code: Int): NetworkException = object : NetworkException("", null) {
        override fun getErrorCode() = code

        override fun isImmediatelyRetryable() = false
    }

    private class Renamed : IOException()
}
