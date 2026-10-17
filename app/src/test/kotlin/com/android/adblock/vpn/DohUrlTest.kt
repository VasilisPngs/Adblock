package com.android.adblock.vpn

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DohUrlTest {
    @Test
    fun `accepts an https endpoint and trims it`() {
        assertEquals(
            "https://adblock.example.workers.dev/dns-query/0123abcd",
            dohUrl("  https://adblock.example.workers.dev/dns-query/0123abcd \n")
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
            "https://adblock.example.workers.dev/dns-query#fragment"
        ).forEach { assertNull(dohUrl(it), it) }
    }

    @Test
    fun `asks with the message in the dns parameter`() {
        val message = byteArrayOf(0, 0, 1, 0, -5, -1)

        assertEquals("https://dns.example/dns-query?dns=AAABAPv_", dohRequest("https://dns.example/dns-query", message))
        assertEquals("https://dns.example/q?id=1&dns=AAABAPv_", dohRequest("https://dns.example/q?id=1", message))
    }
}
