package com.vasilispngs.adblock.vpn

import java.net.URI
import java.util.Base64

internal fun dohUrl(text: String): String? {
    val url = text.trim()
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    return url.takeIf { uri.scheme == "https" && validDohHost(host) && uri.rawFragment == null }
}

private fun validDohHost(host: String): Boolean {
    if (host.startsWith('[') && host.endsWith(']')) return host.length > 2
    val labels = host.trimEnd('.').split('.').map(String::toByteArray)
    return labels.all { it.size in 1..MAX_DNS_LABEL } && labels.sumOf { it.size + 1 } + 1 <= MAX_DNS_NAME
}

internal fun dohRequest(url: String, message: ByteArray): String =
    url + (if ('?' in url) '&' else '?') + "dns=" + Base64.getUrlEncoder().withoutPadding().encodeToString(message)
