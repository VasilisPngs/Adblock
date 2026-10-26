package com.vasilispngs.adblock.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DnsMessageTest {
    private fun question(message: ByteArray) = readQuestion(message)!!

    @Test
    fun `reads the question in lower case and rejects malformed ones`() {
        val read = question(query("WWW.Example.Test", Type.AAAA))
        assertEquals("www.example.test", read.name)
        assertEquals(Type.AAAA, read.type)
        assertEquals(1, read.qclass)
        assertNull(readQuestion(query("a.test", questions = 2)))
        assertNull(readQuestion(query("a.test").copyOf(15)))
        assertNull(readQuestion(ByteArray(12)))
        assertNull(readQuestion(query("a.test").also { it[12] = 64 }))
    }

    @Test
    fun `blocks A with 0_0_0_0, AAAA with a zero address and every other type with an empty answer and SOA`() {
        val a = query("ads.example", Type.A, id = 0xBEEF)
        val blockedA = parse(blockedResponse(a, question(a), 60))
        assertEquals(0xBEEF, blockedA.id)
        assertTrue(blockedA.qr && blockedA.rd && blockedA.ra)
        assertEquals(0, blockedA.rcode)
        assertEquals("ads.example", blockedA.question.name)
        assertEquals(listOf(Triple("ads.example", Type.A, 60L)), blockedA.answers.map { Triple(it.name, it.type, it.ttl) })
        assertEquals("0.0.0.0", blockedA.answers.single().data)

        val aaaa = query("ads.example", Type.AAAA)
        assertEquals(List(16) { 0 }, parse(blockedResponse(aaaa, question(aaaa), 60)).answers.single().data)

        for (type in listOf(Type.HTTPS, Type.TXT, Type.CNAME)) {
            val other = query("ads.example", type)
            val blocked = parse(blockedResponse(other, question(other), 60))
            assertEquals(0, blocked.rcode)
            assertTrue(blocked.answers.isEmpty())
            assertEquals(Type.SOA, blocked.authority.single().type)
            assertEquals(60L, blocked.authority.single().ttl)
            assertEquals(60L, blocked.authority.single().data)
        }
    }

    @Test
    fun `servfail and BADVERS keep the question and drop every record`() {
        val message = query("a.test", edns = true)
        val failed = parse(servfail(message, question(message)))
        assertEquals(2, failed.rcode)
        assertEquals(listOf(1, 0, 0, 0), failed.counts)
        val client = Client(edns = true, version = 0, dnssecOk = false, checkingDisabled = false, padding = false)
        val bad = parse(respond(badVersion(message, question(message)), client, EXTENDED_BADVERS))
        assertEquals(0, bad.rcode)
        assertEquals(EXTENDED_BADVERS, bad.opt!!.extended)
    }

    @Test
    fun `answers a query without a question with a format error`() {
        val error = formatError(query("a.test", questions = 2))
        assertEquals(12, error.size)
        assertEquals(1, error[3].toInt() and 0x0F)
        assertTrue(error[2].toInt() and 0x80 != 0)
    }

    @Test
    fun `normalizes the query to one clean OPT and reads what the client asked for`() {
        val message = query("a.test", edns = true, dnssecOk = true, checkingDisabled = true, padding = true)
        val normalized = normalizeQuery(message, question(message))
        with(normalized.client) {
            assertTrue(edns)
            assertEquals(0, version)
            assertTrue(dnssecOk)
            assertTrue(checkingDisabled)
            assertTrue(padding)
        }
        val sent = parse(normalized.forward)
        assertEquals(listOf(1, 0, 0, 1), sent.counts)
        assertEquals(1232, sent.opt!!.udp)
        assertTrue(sent.opt.dnssecOk)
        assertTrue(sent.opt.options.isEmpty())
        assertEquals(0x10, normalized.forward[3].toInt() and 0x10)

        val plain = query("a.test")
        val normalizedPlain = normalizeQuery(plain, question(plain))
        assertFalse(normalizedPlain.client.edns)
        assertFalse(parse(normalizedPlain.forward).opt!!.dnssecOk)
        assertEquals(1, normalizeQuery(query("a.test", edns = true, version = 1), question(query("a.test"))).client.version)
    }

    @Test
    fun `follows CNAME targets through compression pointers`() {
        val message = query("www.site.test")
        val reply = answer(
            message,
            answers = listOf(
                TestRecord(Type.CNAME, "edge.cdn-tracker.example"),
                TestRecord(Type.CNAME, name = "edge.cdn-tracker.example", raw = byteArrayOf(4) + "last".toByteArray() + byteArrayOf(0xC0.toByte(), 0x0C)),
                TestRecord(Type.A, "192.0.2.1", name = "last.www.site.test")
            )
        )
        assertEquals(listOf("edge.cdn-tracker.example", "last.www.site.test"), cnameTargets(reply))
        assertTrue(cnameTargets(answer(message, answers = listOf(TestRecord(Type.A, "192.0.2.1")))).isEmpty())
    }

    @Test
    fun `takes the minimum TTL from answers, then from the SOA of a negative answer`() {
        val message = query("a.test")
        assertEquals(120L, minimumTtl(answer(message, answers = listOf(TestRecord(Type.A, "192.0.2.1", 900), TestRecord(Type.A, "192.0.2.2", 120)))))
        assertEquals(90L, minimumTtl(answer(message, rcode = 3, authority = listOf(TestRecord(Type.SOA, ttl = 600, minimum = 90, name = "test")))))
        assertEquals(30L, minimumTtl(answer(message, rcode = 3, authority = listOf(TestRecord(Type.SOA, ttl = 30, minimum = 900, name = "test")))))
        assertEquals(0L, minimumTtl(answer(message)))
    }

    @Test
    fun `rewrites TTLs of answers and authority without touching the OPT record`() {
        val message = query("a.test", edns = true, dnssecOk = true)
        val reply = {
            answer(
                message,
                answers = listOf(TestRecord(Type.A, "192.0.2.1", 100)),
                authority = listOf(TestRecord(Type.SOA, ttl = 40, minimum = 60, name = "test"))
            )
        }
        val aged = parse(decrementTtl(reply(), 70))
        assertEquals(listOf(30L, 1L), listOf(aged.answers[0].ttl, aged.authority[0].ttl))
        assertTrue(aged.opt!!.dnssecOk)
        assertEquals(0, aged.opt.extended)
        assertEquals(listOf(30L), parse(setTtl(reply(), 30)).answers.map(ParsedRecord::ttl))
        val boosted = parse(boostTtl(reply(), 60))
        assertEquals(listOf(100L, 60L), listOf(boosted.answers[0].ttl, boosted.authority[0].ttl))
        assertTrue(boosted.opt!!.dnssecOk)
        assertEquals(listOf(40L), parse(capTtl(reply(), 40)).answers.map(ParsedRecord::ttl))
    }

    @Test
    fun `accepts an upstream answer only when ID, opcode and question all match`() {
        val message = query("a.test", id = 7)
        val good = answer(message, answers = listOf(TestRecord(Type.A, "192.0.2.1")))
        assertTrue(answersQuery(message, good))
        assertFalse(answersQuery(message, answer(message, id = 8)))
        assertFalse(answersQuery(message, answer(query("b.test", id = 7))))
        assertFalse(answersQuery(message, answer(query("a.test", Type.AAAA, id = 7))))
        assertFalse(answersQuery(message, good.copyOf().also { it[2] = (it[2].toInt() and 0x7F).toByte() }))
        assertFalse(answersQuery(message, good.copyOf().also { it[2] = (it[2].toInt() or 0x10).toByte() }))
        assertFalse(answersQuery(message, good.copyOf(11)))
    }

    @Test
    fun `strips the OPT record and reads extended error codes`() {
        val message = query("a.test", edns = true)
        val failed = answer(message, rcode = 2, ede = 22)
        assertEquals(1, edeOptions(failed).size)
        assertEquals(0, extendedRcode(failed))
        val stripped = withoutOpt(failed)
        assertNull(parse(stripped).opt)
        assertEquals(0, parse(stripped).counts[3])
        val plain = answer(query("a.test"))
        assertSame(plain, withoutOpt(plain))
        assertTrue(edeOptions(plain).isEmpty())
        assertFalse(cacheable(failed))
        assertTrue(cacheable(answer(message, rcode = 3)))
    }

    @Test
    fun `builds the OPT per client, none for plain DNS and padding to 468-byte blocks when asked`() {
        val body = answer(query("a.test"), answers = listOf(TestRecord(Type.A, "192.0.2.1")))
        assertSame(body, respond(body, Client(false, 0, false, false, false)))
        val edns = parse(respond(body, Client(true, 0, true, false, false), 0, listOf(byteArrayOf(0, 15, 0, 2, 0, 22))))
        assertEquals(1, edns.counts[3])
        assertTrue(edns.opt!!.dnssecOk)
        assertEquals(listOf(15 to 2), edns.opt.options.map { it.code to it.length })
        val padded = respond(body, Client(true, 0, false, false, true))
        assertEquals(0, padded.size % 468)
        assertEquals(12, parse(padded).opt!!.options.last().code)
        val huge = body.copyOf(65530)
        assertSame(huge, respond(huge, Client(true, 0, false, false, false)))
    }

    @Test
    fun `builds a recursive A query for a name`() {
        val expected = byteArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) +
            byteArrayOf(7) + "example".toByteArray() + byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0) +
            byteArrayOf(0, 1, 0, 1)
        assertArrayEquals(expected, addressQuery("example.com"))
    }

    @Test
    fun `never throws on corrupted or truncated packets`() {
        val random = Random(0x5EED)
        val message = query("www.site.test", edns = true, dnssecOk = true)
        val samples = listOf(
            answer(message, answers = listOf(TestRecord(Type.CNAME, "edge.cdn-tracker.example"), TestRecord(Type.A, "192.0.2.1", name = "edge.cdn-tracker.example"))),
            answer(message, rcode = 3, authority = listOf(TestRecord(Type.SOA, ttl = 60, minimum = 60, name = "test"))),
            answer(message, rcode = 2, ede = 22)
        )
        repeat(4000) { round ->
            var packet = samples[round % samples.size].copyOf()
            repeat(1 + random.nextInt(4)) { packet[random.nextInt(packet.size)] = random.nextInt(256).toByte() }
            if (random.nextFloat() < 0.3f) packet = packet.copyOf(random.nextInt(packet.size))
            val read = readQuestion(packet)
            cnameTargets(packet)
            minimumTtl(packet)
            soaMinimum(packet)
            decrementTtl(packet.copyOf(), 5)
            boostTtl(packet.copyOf(), 60)
            extendedRcode(packet)
            edeOptions(packet)
            withoutOpt(packet)
            answersQuery(message, packet)
            if (read != null) {
                normalizeQuery(packet, read)
                blockedResponse(packet, read, 60)
                servfail(packet, read)
            }
        }
    }
}
