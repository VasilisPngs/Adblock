package com.vasilispngs.adblock.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DohUrlTest {
    @Test
    fun `accepts an https URL and trims it`() {
        assertEquals("https://cloudflare-dns.com/dns-query", httpsUrl("  https://cloudflare-dns.com/dns-query \n"))
    }

    @Test
    fun `rejects anything that is not an https URL`() {
        listOf(
            "",
            "cloudflare-dns.com/dns-query",
            "http://cloudflare-dns.com/dns-query",
            "https://",
            "https:///dns-query",
            "https://bad host/dns-query",
            "https://cloudflare-dns.com/dns-query#fragment"
        ).forEach { assertNull(it, httpsUrl(it)) }
    }

    @Test
    fun `asks with the message in the dns parameter`() {
        val message = byteArrayOf(0, 0, 1, 0, -5, -1)

        assertEquals("https://dns.example/dns-query?dns=AAABAPv_", dohRequest("https://dns.example/dns-query", message))
        assertEquals("https://dns.example/q?id=1&dns=AAABAPv_", dohRequest("https://dns.example/q?id=1", message))
    }
}
