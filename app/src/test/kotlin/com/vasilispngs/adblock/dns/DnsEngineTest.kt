package com.vasilispngs.adblock.dns

import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.LoggedQuery
import com.vasilispngs.adblock.data.Origin
import com.vasilispngs.adblock.data.unique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsEngineTest {
    private var now = 1_800_000_000_000L
    private val zone = mutableMapOf<String, (ByteArray) -> ByteArray?>()
    private val handled = mutableListOf<Pair<String, Parsed>>()
    private val logged = mutableListOf<LoggedQuery>()
    private val engine = DnsEngine(
        resolver = { url, message, onResult ->
            val asked = parse(message)
            handled += url to asked
            val reply = zone["${asked.question.name}|${asked.question.type}"]
            onResult(if (reply != null) reply(message) else answer(message, rcode = 3, authority = listOf(negative(120))))
        },
        log = logged::add,
        clock = { now }
    ).apply {
        state = EngineState(blocklist = Blocklist(unique(listOf("ads.example", "tracker.test", "cdn-tracker.example").map(::hostHash).toLongArray())))
    }

    private fun negative(minimum: Long) = TestRecord(Type.SOA, ttl = 300, minimum = minimum, name = "site.test")

    private fun a(data: String, ttl: Long = 300, name: String? = null) = TestRecord(Type.A, data, ttl, name)

    private fun serve(name: String, type: Int = Type.A, reply: (ByteArray) -> ByteArray?) {
        zone["$name|$type"] = reply
    }

    private fun ask(message: ByteArray): Parsed {
        var reply: ByteArray? = null
        engine.handle(message) { reply = it }
        return parse(reply!!)
    }

    private fun rows(name: String) = logged.filter { it.name == name }.map { Triple(it.action, it.origin, it.rule) }

    @Test
    fun `answers listed names locally with a 60 second block and keeps the client's letter case`() {
        val a = ask(query("X.Ads.Example", Type.A, id = 0xABCD))
        assertEquals(0xABCD, a.id)
        assertEquals("X.Ads.Example", a.question.name)
        assertEquals(listOf("0.0.0.0" to 60L), a.answers.map { it.data to it.ttl })
        assertEquals(List(16) { 0 }, ask(query("ads.example", Type.AAAA)).answers.single().data)
        val https = ask(query("ads.example", Type.HTTPS))
        assertEquals(listOf(0, 0, Type.SOA), listOf(https.rcode, https.answers.size, https.authority.single().type))
        assertTrue(handled.isEmpty())
        assertEquals(listOf(Triple(Action.Block, Origin.List, "ads.example")), rows("x.ads.example"))
    }

    @Test
    fun `forwards other names to the resolver once and serves repeats from cache with TTL floor 60`() {
        serve("www.site.test") { answer(it, answers = listOf(a("192.0.2.10", 30))) }
        val first = ask(query("www.site.test", id = 0x1111))
        assertEquals(1232, handled.single().second.opt!!.udp)
        assertEquals(DEFAULT_RESOLVER_FOR_TEST, handled.single().first)
        assertEquals(0x1111, first.id)
        assertEquals(listOf("192.0.2.10" to 60L), first.answers.map { it.data to it.ttl })

        now += 10_000
        val second = ask(query("WWW.site.test", id = 0x2222))
        assertEquals(1, handled.size)
        assertEquals(0x2222, second.id)
        assertEquals("WWW.site.test", second.question.name)
        assertEquals("192.0.2.10", second.answers.single().data)
        assertEquals(50L, second.answers.single().ttl)
        assertEquals(listOf(Triple(Action.Allow, Origin.None, null), Triple(Action.Allow, Origin.Cache, null)), rows("www.site.test"))
    }

    @Test
    fun `keeps DNSSEC-OK and checking-disabled answers apart but shares one entry between plain and EDNS clients`() {
        serve("keys.site.test") { answer(it, answers = listOf(a("192.0.2.11"))) }
        assertTrue(ask(query("keys.site.test", edns = true, dnssecOk = true)).opt!!.dnssecOk)
        val edns = ask(query("keys.site.test", edns = true))
        assertEquals(2, handled.size)
        assertEquals(1, edns.counts[3])
        val plain = ask(query("keys.site.test"))
        assertEquals(2, handled.size)
        assertNull(plain.opt)
        assertEquals(0, plain.counts[3])
        ask(query("keys.site.test", checkingDisabled = true))
        assertEquals(3, handled.size)
        var padded: ByteArray? = null
        engine.handle(query("keys.site.test", edns = true, padding = true)) { padded = it }
        assertEquals(0, padded!!.size % 468)
    }

    @Test
    fun `answers unsupported EDNS versions with BADVERS without asking upstream`() {
        val reply = ask(query("version.site.test", edns = true, version = 1))
        assertEquals(1, reply.opt!!.extended)
        assertTrue(reply.answers.isEmpty())
        assertTrue(handled.isEmpty())
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `caches NXDOMAIN for the SOA minimum`() {
        assertEquals(3, ask(query("missing.site.test")).rcode)
        now += 60_000
        assertEquals(3, ask(query("missing.site.test")).rcode)
        assertEquals(1, handled.size)
        assertEquals(Origin.Cache, logged.last().origin)
        now += 61_000
        assertEquals(3, ask(query("missing.site.test")).rcode)
        assertEquals(2, handled.size)
        assertEquals(Origin.Stale, logged.last().origin)
    }

    @Test
    fun `never caches upstream failures and passes the upstream error through`() {
        serve("down.site.test") { null }
        serve("broken.site.test") { answer(it, rcode = 2, ede = 22) }
        serve("refused.site.test") { answer(it, rcode = 5) }
        repeat(2) { assertEquals(2, ask(query("down.site.test")).rcode) }
        assertEquals(2, handled.count { it.second.question.name == "down.site.test" })
        assertTrue(rows("down.site.test").isEmpty())

        val broken = ask(query("broken.site.test", edns = true))
        assertEquals(2, broken.rcode)
        assertTrue(broken.opt!!.options.any { it.code == 15 })

        ask(query("refused.site.test"))
        assertEquals(5, ask(query("refused.site.test")).rcode)
        assertEquals(2, handled.count { it.second.question.name == "refused.site.test" })
        assertEquals(2, rows("refused.site.test").size)
    }

    @Test
    fun `blocks answers that hide a listed host behind a CNAME, also when served from cache`() {
        serve("shop.site.test") {
            answer(it, answers = listOf(TestRecord(Type.CNAME, "edge.cdn-tracker.example"), a("192.0.2.5", name = "edge.cdn-tracker.example")))
        }
        repeat(2) { assertEquals(listOf<Any?>("0.0.0.0"), ask(query("shop.site.test")).answers.map(ParsedRecord::data)) }
        assertEquals(1, handled.size)
        assertEquals(List(2) { Triple(Action.Block, Origin.Cname, "cdn-tracker.example") }, rows("shop.site.test"))
    }

    @Test
    fun `an allow rule on the name skips the CNAME check`() {
        serve("trusted.site.test") {
            answer(it, answers = listOf(TestRecord(Type.CNAME, "edge.cdn-tracker.example"), a("192.0.2.5", name = "edge.cdn-tracker.example")))
        }
        engine.state = EngineState(rules = Rules(allow = setOf("trusted.site.test")), blocklist = engine.state.blocklist)
        assertEquals(listOf<Any?>("edge.cdn-tracker.example", "192.0.2.5"), ask(query("trusted.site.test")).answers.map(ParsedRecord::data))
        assertEquals(listOf(Triple(Action.Allow, Origin.Allow, "trusted.site.test")), rows("trusted.site.test"))
    }

    @Test
    fun `custom rules take effect at once and removing them restores resolution`() {
        serve("a.blockme.site.test") { answer(it, answers = listOf(a("192.0.2.20"))) }
        val list = engine.state.blocklist
        engine.state = EngineState(rules = Rules(block = setOf("blockme.site.test")), blocklist = list)
        assertEquals("0.0.0.0", ask(query("a.blockme.site.test")).answers.single().data)
        assertEquals(listOf(Triple(Action.Block, Origin.Custom, "blockme.site.test")), rows("a.blockme.site.test"))
        engine.state = EngineState(blocklist = list)
        assertEquals("192.0.2.20", ask(query("a.blockme.site.test")).answers.single().data)
        assertEquals(1, handled.size)
    }

    @Test
    fun `logs every query and nothing while logging is off`() {
        serve("logme.site.test") { answer(it, answers = listOf(a("192.0.2.40"))) }
        serve("quiet.site.test") { answer(it, answers = listOf(a("192.0.2.41"))) }
        ask(query("logme.site.test"))
        ask(query("logme.site.test"))
        assertEquals(2, rows("logme.site.test").size)
        assertEquals(listOf(now, now), logged.map(LoggedQuery::at))
        assertEquals(listOf("A", "A"), logged.map(LoggedQuery::type))
        engine.state = EngineState(blocklist = engine.state.blocklist, logEnabled = false)
        ask(query("quiet.site.test"))
        assertTrue(rows("quiet.site.test").isEmpty())
    }

    @Test
    fun `serves an expired answer for six hours while it refreshes it at most every five seconds`() {
        var version = 1
        serve("www.site.test") { answer(it, answers = listOf(a("192.0.2.${50 + version}", 60))) }
        ask(query("www.site.test"))
        now += 120_000
        version = 2
        val stale = ask(query("www.site.test"))
        assertEquals("192.0.2.51", stale.answers.single().data)
        assertEquals(30L, stale.answers.single().ttl)
        assertEquals(2, handled.size)
        assertEquals(Origin.Stale, logged.last().origin)
        assertEquals("192.0.2.52", ask(query("www.site.test")).answers.single().data)

        now += 6 * 3_600_000L + 61_000
        version = 3
        assertEquals("192.0.2.53", ask(query("www.site.test")).answers.single().data)
        assertEquals(Origin.None, logged.last().origin)
    }

    @Test
    fun `a negative answer is never fresher than its SOA minimum and one with a zero minimum is asked again`() {
        serve("gone.site.test") { answer(it, rcode = 3, authority = listOf(negative(20))) }
        serve("zero.site.test") { answer(it, rcode = 3, authority = listOf(negative(0))) }
        assertTrue(ask(query("gone.site.test")).authority.single().ttl <= 20)
        ask(query("zero.site.test"))
        now += 60_000
        val before = handled.size
        assertTrue(ask(query("gone.site.test")).authority.single().ttl <= 20)
        ask(query("zero.site.test"))
        assertTrue(handled.drop(before).any { it.second.question.name == "zero.site.test" })
    }

    @Test
    fun `a replayed answer ages the TTLs of its additional records too`() {
        serve("www.site.test") { answer(it, answers = listOf(a("192.0.2.60")), additional = listOf(a("192.0.2.61", name = "ns.site.test"))) }
        ask(query("www.site.test"))
        now += 100_000
        val replayed = ask(query("www.site.test"))
        assertEquals(200L, replayed.answers.single().ttl)
        assertEquals(200L, replayed.additional.single().ttl)
    }

    @Test
    fun `answers cached from one resolver are not reused after switching to another`() {
        val other = "https://security.cloudflare-dns.com/dns-query"
        serve("www.site.test") { answer(it, answers = listOf(a("192.0.2.70"))) }
        ask(query("www.site.test"))
        engine.state = EngineState(blocklist = engine.state.blocklist, resolver = other)
        ask(query("www.site.test"))
        assertEquals(listOf(DEFAULT_RESOLVER_FOR_TEST, other), handled.map { it.first })
    }

    @Test
    fun `answers a query without a question with a format error and drops runts`() {
        var reply: ByteArray? = null
        engine.handle(query("a.test", questions = 2)) { reply = it }
        assertEquals(1, reply!![3].toInt() and 0x0F)
        reply = null
        engine.handle(ByteArray(4)) { reply = it }
        assertNull(reply)
    }

    private companion object {
        const val DEFAULT_RESOLVER_FOR_TEST = "https://cloudflare-dns.com/dns-query"
    }
}
