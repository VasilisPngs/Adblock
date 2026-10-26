package com.vasilispngs.adblock.vpn

import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private const val DNS_MESSAGE = "application/dns-message"
private const val HTTP_OK = 200
private const val READ_SIZE = 4096
private const val QR = 0x80

sealed interface DohResult {
    class Answer(val message: ByteArray) : DohResult
    data class Rejected(val status: Int) : DohResult
    data object Failed : DohResult
}

internal fun dnsAnswer(query: ByteArray, response: ByteArray): DohResult =
    if (response.size >= 12 && response[0] == query[0] && response[1] == query[1] && response[2].toInt() and QR != 0) {
        DohResult.Answer(response)
    } else {
        DohResult.Failed
    }

class DohClient(private val engine: HttpEngine) {
    private val executor = Executors.newSingleThreadExecutor()

    fun resolve(url: String, query: ByteArray, onResult: (DohResult) -> Unit) {
        request(url, query, onResult).start()
    }

    suspend fun resolve(url: String, query: ByteArray): DohResult = suspendCancellableCoroutine { continuation ->
        val request = request(url, query) { continuation.resume(it) }
        continuation.invokeOnCancellation { request.cancel() }
        request.start()
    }

    private fun request(url: String, query: ByteArray, onResult: (DohResult) -> Unit): UrlRequest =
        engine.newUrlRequestBuilder(dohRequest(url, query), executor, Exchange(query, onResult))
            .addHeader("Accept", DNS_MESSAGE)
            .build()

    private class Exchange(private val query: ByteArray, private val onResult: (DohResult) -> Unit) : UrlRequest.Callback {
        private val body = ByteArrayOutputStream()
        private val buffer = ByteBuffer.allocateDirect(READ_SIZE)
        private var result: DohResult = DohResult.Failed

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

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) = onResult(dnsAnswer(query, body.toByteArray()))

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) = onResult(DohResult.Failed)

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) = onResult(result)
    }
}
