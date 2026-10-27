package com.vasilispngs.adblock.vpn

import java.net.URI
import java.util.Base64

internal fun dohUrl(text: String): String? {
    val url = text.trim()
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    return url.takeIf { uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.rawFragment == null }
}

internal fun dohRequest(url: String, message: ByteArray): String =
    url + (if ('?' in url) '&' else '?') + "dns=" + Base64.getUrlEncoder().withoutPadding().encodeToString(message)
