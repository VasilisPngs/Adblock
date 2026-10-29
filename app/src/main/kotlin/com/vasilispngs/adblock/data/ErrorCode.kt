package com.vasilispngs.adblock.data

import android.net.http.NetworkException
import java.io.IOException

sealed interface ErrorCode {
    data class Http(val status: Int) : ErrorCode
    data class Named(val name: String) : ErrorCode
}

class HttpStatusException(val status: Int) : IOException("HTTP $status")

internal fun Throwable.errorCode(): ErrorCode = when (this) {
    is HttpStatusException -> ErrorCode.Http(status)
    is NetworkException -> ErrorCode.Named(errorName)
    else -> ErrorCode.Named(
        generateSequence<Class<*>>(javaClass) { it.superclass }
            .first { it.classLoader === Throwable::class.java.classLoader }
            .simpleName
    )
}

private val NetworkException.errorName: String
    get() = when (errorCode) {
        NetworkException.ERROR_HOSTNAME_NOT_RESOLVED -> "ERROR_HOSTNAME_NOT_RESOLVED"
        NetworkException.ERROR_INTERNET_DISCONNECTED -> "ERROR_INTERNET_DISCONNECTED"
        NetworkException.ERROR_NETWORK_CHANGED -> "ERROR_NETWORK_CHANGED"
        NetworkException.ERROR_TIMED_OUT -> "ERROR_TIMED_OUT"
        NetworkException.ERROR_CONNECTION_CLOSED -> "ERROR_CONNECTION_CLOSED"
        NetworkException.ERROR_CONNECTION_TIMED_OUT -> "ERROR_CONNECTION_TIMED_OUT"
        NetworkException.ERROR_CONNECTION_REFUSED -> "ERROR_CONNECTION_REFUSED"
        NetworkException.ERROR_CONNECTION_RESET -> "ERROR_CONNECTION_RESET"
        NetworkException.ERROR_ADDRESS_UNREACHABLE -> "ERROR_ADDRESS_UNREACHABLE"
        NetworkException.ERROR_QUIC_PROTOCOL_FAILED -> "ERROR_QUIC_PROTOCOL_FAILED"
        NetworkException.ERROR_OTHER -> "ERROR_OTHER"
        else -> errorCode.toString()
    }
