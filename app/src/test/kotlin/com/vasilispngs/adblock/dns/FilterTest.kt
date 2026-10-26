package com.vasilispngs.adblock.dns

import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.Origin
import com.vasilispngs.adblock.data.unique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterTest {
    private val listed = listOf("ads.example", "tracker.test", "cdn-tracker.example", "sub.parentless.test", "metrics.example.net")
    private val blocklist = Blocklist(unique(listed.map(::hostHash).toLongArray()))
    private val none = Rules()

    private fun Verdict.parts() = Triple(action, rule, origin)

    @Test
    fun `walks from the full name up to the registrable parent, never the bare TLD`() {
        val name = "a.b.example.test"
        assertEquals(listOf("a.b.example.test", "b.example.test", "example.test"), ancestors(name).map(name::substring))
        assertEquals(listOf("example.test"), ancestors("example.test").map("example.test"::substring))
        assertEquals(listOf("localhost"), ancestors("localhost").map("localhost"::substring))
    }

    @Test
    fun `finds every listed host and its subdomains`() {
        for (host in listed) {
            assertEquals(Triple(Action.Block, host, Origin.List), decide(host, none, blocklist).parts())
            assertEquals(Triple(Action.Block, host, Origin.List), decide("deep.sub.$host", none, blocklist).parts())
        }
    }

    @Test
    fun `allows names next to listed ones`() {
        for (host in listed) {
            val first = host.substringBefore('.')
            val rest = host.substringAfter('.')
            for (name in listOf("${host}x", "x$host", "${first.dropLast(1)}.$rest", "${first}a.$rest", "$host.other")) {
                assertEquals(name, Triple(Action.Allow, null, Origin.None), decide(name, none, blocklist).parts())
            }
        }
    }

    @Test
    fun `blocks a listed subdomain without blocking its parent`() {
        assertEquals(Action.Block, decide("sub.parentless.test", none, blocklist).action)
        assertEquals(Action.Allow, decide("parentless.test", none, blocklist).action)
        assertEquals(Action.Allow, decide("other.parentless.test", none, blocklist).action)
    }

    @Test
    fun `lets the most specific custom rule win, allow beating block on the same name, and both beating the list`() {
        val rules = Rules(
            allow = setOf("ads.example", "shared.test", "safe.blocked.test"),
            block = setOf("blocked.test", "shared.test")
        )
        assertEquals(Triple(Action.Allow, "ads.example", Origin.Allow), decide("x.ads.example", rules, blocklist).parts())
        assertEquals(Triple(Action.Allow, "shared.test", Origin.Allow), decide("shared.test", rules, blocklist).parts())
        assertEquals(Triple(Action.Block, "blocked.test", Origin.Custom), decide("a.blocked.test", rules, blocklist).parts())
        assertEquals(Triple(Action.Allow, "safe.blocked.test", Origin.Allow), decide("a.safe.blocked.test", rules, blocklist).parts())
        assertEquals(Triple(Action.Block, "tracker.test", Origin.List), decide("tracker.test", rules, blocklist).parts())
    }

    @Test
    fun `an empty blocklist blocks nothing`() {
        assertFalse(Blocklist().contains("ads.example"))
        assertEquals(0, Blocklist().size)
        assertNull(decide("ads.example", none, Blocklist()).rule)
    }

    @Test
    fun `hashes a suffix the same way as the separate name`() {
        assertEquals(hostHash("example.test"), hostHash("www.example.test", 4))
        assertTrue(blocklist.contains("www.tracker.test", 4))
    }
}
