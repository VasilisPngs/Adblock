package com.vasilispngs.adblock.vpn

import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import com.vasilispngs.adblock.data.ErrorCode
import com.vasilispngs.adblock.data.errorCode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val DNS_MESSAGE = "application/dns-message"
private const val HTTP_OK = 200
private const val READ_SIZE = 4096
private const val QR = 0x80
private const val REQUEST_TIMEOUT_MILLIS = 10_000L
private const val ERROR_TIMED_OUT = "ERROR_TIMED_OUT"

sealed interface DohResult {
    class Answer(val message: ByteArray) : DohResult
    data class Rejected(val status: Int) : DohResult
    data object Failed : DohResult
    data class Unreachable(val code: ErrorCode) : DohResult
}

internal fun DohResult.errorCode(): ErrorCode? = when (this) {
    is DohResult.Answer -> null
    is DohResult.Rejected -> ErrorCode.Http(status)
    DohResult.Failed -> ErrorCode.Http(HTTP_OK)
    is DohResult.Unreachable -> code
}

internal fun dnsAnswer(query: ByteArray, response: ByteArray): DohResult =
    if (response.size >= 12 && response[0] == query[0] && response[1] == query[1] && response[2].toInt() and QR != 0) {
        DohResult.Answer(response)
    } else {
        DohResult.Failed
    }

class DohClient(private val engine: HttpEngine, private val timeoutMillis: Long = REQUEST_TIMEOUT_MILLIS) {
    private val executor = Executors.newSingleThreadScheduledExecutor()

    fun resolve(url: String, query: ByteArray, onResult: (DohResult) -> Unit) {
        val _ = start(url, query, onResult)
    }

    suspend fun resolve(url: String, query: ByteArray): DohResult = suspendCancellableCoroutine { continuation ->
        val request = start(url, query) { if (continuation.isActive) continuation.resume(it) }
        continuation.invokeOnCancellation { request.cancel() }
    }

    private fun start(url: String, query: ByteArray, onResult: (DohResult) -> Unit): UrlRequest {
        val exchange = Exchange(query, onResult)
        val request = engine.newUrlRequestBuilder(dohRequest(url, query), executor, exchange)
            .addHeader("Accept", DNS_MESSAGE)
            .build()
        exchange.timeout = executor.schedule(
            { exchange.expire(request) },
            timeoutMillis,
            TimeUnit.MILLISECONDS
        )
        request.start()
        return request
    }

    private class Exchange(private val query: ByteArray, private val onResult: (DohResult) -> Unit) : UrlRequest.Callback {
        private val body = ByteArrayOutputStream()
        private val buffer = ByteBuffer.allocateDirect(READ_SIZE)
        private val done = AtomicBoolean()
        private var result: DohResult = DohResult.Failed

        @Volatile
        var timeout: ScheduledFuture<*>? = null

        fun expire(request: UrlRequest) {
            val timedOut = DohResult.Unreachable(ErrorCode.Named(ERROR_TIMED_OUT))
            result = timedOut
            request.cancel()
            finish(timedOut)
        }

        private fun finish(outcome: DohResult) {
            timeout?.cancel(false)
            if (done.compareAndSet(false, true)) onResult(outcome)
        }

        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) =
            request.followRedirect()

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            if (info.httpStatusCode == HTTP_OK) {
                request.read(buffer)
            } else {
                result = DohResult.Rejected(info.httpStatusCode)
                request.cancel()
            }
        }

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
            byteBuffer.flip()
            body.write(ByteArray(byteBuffer.remaining()).also(byteBuffer::get))
            byteBuffer.clear()
            if (body.size() > MAX_DNS_MESSAGE) request.cancel() else request.read(byteBuffer)
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) = finish(dnsAnswer(query, body.toByteArray()))

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) =
            finish(DohResult.Unreachable(error.errorCode()))

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) = finish(result)
    }
}
