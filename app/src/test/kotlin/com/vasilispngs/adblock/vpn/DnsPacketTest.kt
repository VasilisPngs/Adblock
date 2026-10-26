package com.vasilispngs.adblock.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPacketTest {
    private val client = byteArrayOf(10, 111, 0, 1)
    private val server = byteArrayOf(10, 111, 0, 53)
    private val message = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) + byteArrayOf(7) + "example".toByteArray() + byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0, 0, 1, 0, 1)

    @Test
    fun `reads the DNS message of a UDP query to port 53`() {
        val packet = udpPacket(destinationPort = 53, payload = message)

        val query = parseDnsQuery(packet, packet.size)!!

        assertArrayEquals(client, query.source)
        assertArrayEquals(server, query.destination)
        assertEquals(CLIENT_PORT, query.sourcePort)
        assertArrayEquals(message, query.message)
    }

    @Test
    fun `reads only the bytes that were read from the interface`() {
        val packet = udpPacket(destinationPort = 53, payload = message) + ByteArray(64)

        assertArrayEquals(message, parseDnsQuery(packet, packet.size - 64)!!.message)
    }

    @Test
    fun `ignores packets that are not a DNS query over UDP`() {
        assertNull(udpPacket(destinationPort = 853, payload = message).let { parseDnsQuery(it, it.size) })
        assertNull(udpPacket(destinationPort = 53, payload = message, protocol = 6).let { parseDnsQuery(it, it.size) })
        assertNull(udpPacket(destinationPort = 53, payload = ByteArray(4)).let { parseDnsQuery(it, it.size) })
    }

    @Test
    fun `ignores fragments`() {
        assertNull(udpPacket(destinationPort = 53, payload = message, fragment = 0x2000).let { parseDnsQuery(it, it.size) })
        assertNull(udpPacket(destinationPort = 53, payload = message, fragment = 0x0010).let { parseDnsQuery(it, it.size) })
    }

    @Test
    fun `ignores IPv6 and truncated packets`() {
        val packet = udpPacket(destinationPort = 53, payload = message)

        assertNull(parseDnsQuery(packet.copyOf().also { it[0] = 0x60 }, packet.size))
        assertNull(parseDnsQuery(packet, packet.size - 1))
        assertNull(parseDnsQuery(packet, 19))
    }

    @Test
    fun `answers from the queried address to the port that asked`() {
        val packet = udpPacket(destinationPort = 53, payload = message)
        val query = parseDnsQuery(packet, packet.size)!!
        val answer = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)

        val response = dnsResponsePacket(query, answer)

        assertEquals(0x45, response[0].toInt())
        assertEquals(response.size, response.u16(2))
        assertEquals(17, response[9].toInt())
        assertArrayEquals(server, response.copyOfRange(12, 16))
        assertArrayEquals(client, response.copyOfRange(16, 20))
        assertEquals(0xFFFF, onesComplementSum(response, 20))
        assertEquals(53, response.u16(20))
        assertEquals(CLIENT_PORT, response.u16(22))
        assertEquals(8 + answer.size, response.u16(24))
        assertArrayEquals(answer, response.copyOfRange(28, response.size))
    }

    private fun udpPacket(destinationPort: Int, payload: ByteArray, protocol: Int = 17, fragment: Int = 0): ByteArray {
        val packet = ByteArray(28 + payload.size)
        packet[0] = 0x45
        packet.put16(2, packet.size)
        packet.put16(6, fragment)
        packet[8] = 64
        packet[9] = protocol.toByte()
        client.copyInto(packet, 12)
        server.copyInto(packet, 16)
        packet.put16(20, CLIENT_PORT)
        packet.put16(22, destinationPort)
        packet.put16(24, 8 + payload.size)
        payload.copyInto(packet, 28)
        return packet
    }

    private fun ByteArray.u16(offset: Int) = (this[offset].toInt() and 0xFF shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ByteArray.put16(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun onesComplementSum(bytes: ByteArray, length: Int): Int {
        var sum = 0
        for (offset in 0 until length step 2) sum += bytes.u16(offset)
        while (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum
    }

    private companion object {
        const val CLIENT_PORT = 41_234
    }
}
