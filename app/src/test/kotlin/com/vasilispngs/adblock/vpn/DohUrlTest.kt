package com.vasilispngs.adblock.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DohUrlTest {
    @Test
    fun `accepts an https endpoint and trims it`() {
        assertEquals(
            "https://adblock.example.workers.dev/dns-query/0123abcd",
            dohUrl("  https://adblock.example.workers.dev/dns-query/0123abcd \n")
        )
    }

    @Test
    fun `accepts an IPv6 https endpoint`() {
        assertEquals(
            "https://[2606:4700:4700::1111]/dns-query",
            dohUrl("https://[2606:4700:4700::1111]/dns-query")
        )
    }

    @Test
    fun `rejects anything that is not an https endpoint`() {
        listOf(
            "",
            "adblock.example.workers.dev/dns-query",
            "http://adblock.example.workers.dev/dns-query",
            "https://",
            "https:///dns-query",
            "https://bad host/dns-query",
            "https://adblock.example.workers.dev/dns-query#fragment",
            "https://${"a".repeat(64)}.example/dns-query",
            "https://${List(4) { "a".repeat(63) }.joinToString(".")}.example/dns-query"
        ).forEach { assertNull(it, dohUrl(it)) }
    }

    @Test
    fun `asks with the message in the dns parameter`() {
        val message = byteArrayOf(0, 0, 1, 0, -5, -1)

        assertEquals("https://dns.example/dns-query?dns=AAABAPv_", dohRequest("https://dns.example/dns-query", message))
        assertEquals("https://dns.example/q?id=1&dns=AAABAPv_", dohRequest("https://dns.example/q?id=1", message))
    }
}
