package com.vasilispngs.adblock.dns

import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val DNS_MESSAGE = "application/dns-message"
private const val HTTP_OK = 200
private const val READ_SIZE = 4096
private const val MAX_RESPONSE = 0xFFFF
private const val TIMEOUT_MILLIS = 2500L
private const val PROBE_HOST = "example.com"

fun interface Resolver {
    fun resolve(url: String, message: ByteArray, onResult: (ByteArray?) -> Unit)
}

class DohClient(private val engine: HttpEngine) : Resolver {
    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun resolve(url: String, message: ByteArray, onResult: (ByteArray?) -> Unit) {
        val exchange = Exchange(message, onResult)
        val request = engine.newUrlRequestBuilder(dohRequest(url, message), executor, exchange)
            .addHeader("Accept", DNS_MESSAGE)
            .build()
        exchange.timeout = executor.schedule(request::cancel, TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        request.start()
    }

    private class Exchange(private val query: ByteArray, private val onResult: (ByteArray?) -> Unit) : UrlRequest.Callback {
        private val body = ByteArrayOutputStream()
        private val buffer = ByteBuffer.allocateDirect(READ_SIZE)
        var timeout: Future<*>? = null

        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) =
            request.followRedirect()

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            if (info.httpStatusCode == HTTP_OK) request.read(buffer) else request.cancel()
        }

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
            byteBuffer.flip()
            body.write(ByteArray(byteBuffer.remaining()).also(byteBuffer::get))
            byteBuffer.clear()
            if (body.size() > MAX_RESPONSE) request.cancel() else request.read(byteBuffer)
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) =
            finish(body.toByteArray().takeIf { answersQuery(query, it) })

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) = finish(null)

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) = finish(null)

        private fun finish(response: ByteArray?) {
            timeout?.cancel(false)
            onResult(response)
        }
    }
}

suspend fun Resolver.probe(url: String): Boolean = suspendCancellableCoroutine { continuation ->
    resolve(url, addressQuery(PROBE_HOST)) { response ->
        continuation.resume(response != null && rcode(response) == 0)
    }
}
