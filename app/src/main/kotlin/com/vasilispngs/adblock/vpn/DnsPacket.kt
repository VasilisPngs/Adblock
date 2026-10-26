package com.vasilispngs.adblock.vpn

private const val IPV4 = 4
private const val UDP = 17
private const val DNS_PORT = 53
private const val IP_HEADER = 20
private const val UDP_HEADER = 8
private const val DNS_HEADER = 12
private const val TTL = 64
private const val FRAGMENT_MASK = 0x3FFF
private const val TYPE_A = 1
private const val CLASS_IN = 1
private const val RECURSION_DESIRED = 0x0100
private const val MAX_LABEL = 63
internal const val MAX_DNS_MESSAGE = 0xFFFF - IP_HEADER - UDP_HEADER

internal class DnsQuery(
    val source: ByteArray,
    val destination: ByteArray,
    val sourcePort: Int,
    val message: ByteArray
)

internal fun parseDnsQuery(packet: ByteArray, length: Int): DnsQuery? {
    if (length < IP_HEADER || packet[0].toInt() ushr 4 != IPV4) return null
    val headerLength = (packet[0].toInt() and 0x0F) * 4
    val totalLength = packet.u16(2)
    if (headerLength < IP_HEADER || totalLength > length || totalLength < headerLength + UDP_HEADER) return null
    if (packet[9].toInt() != UDP || packet.u16(6) and FRAGMENT_MASK != 0) return null
    if (packet.u16(headerLength + 2) != DNS_PORT) return null
    val udpLength = packet.u16(headerLength + 4)
    if (udpLength < UDP_HEADER + DNS_HEADER || headerLength + udpLength > totalLength) return null
    return DnsQuery(
        source = packet.copyOfRange(12, 16),
        destination = packet.copyOfRange(16, 20),
        sourcePort = packet.u16(headerLength),
        message = packet.copyOfRange(headerLength + UDP_HEADER, headerLength + udpLength)
    )
}

internal fun dnsResponsePacket(query: DnsQuery, message: ByteArray): ByteArray {
    val udpLength = UDP_HEADER + message.size
    val packet = ByteArray(IP_HEADER + udpLength)
    packet[0] = (IPV4 shl 4 or IP_HEADER / 4).toByte()
    packet.put16(2, packet.size)
    packet[8] = TTL.toByte()
    packet[9] = UDP.toByte()
    query.destination.copyInto(packet, 12)
    query.source.copyInto(packet, 16)
    packet.put16(10, headerChecksum(packet))
    packet.put16(IP_HEADER, DNS_PORT)
    packet.put16(IP_HEADER + 2, query.sourcePort)
    packet.put16(IP_HEADER + 4, udpLength)
    message.copyInto(packet, IP_HEADER + UDP_HEADER)
    return packet
}

internal fun serverFailure(query: ByteArray): ByteArray = query.copyOf().apply {
    this[2] = (0x80 or (this[2].toInt() and 0x79)).toByte()
    this[3] = 0x82.toByte()
}

internal fun addressQuery(name: String): ByteArray {
    val labels = name.trimEnd('.').split('.').map(String::toByteArray)
    require(labels.all { it.size in 1..MAX_LABEL })
    val question = labels.fold(byteArrayOf()) { bytes, label -> bytes + label.size.toByte() + label }
    return ByteArray(DNS_HEADER).apply {
        put16(2, RECURSION_DESIRED)
        put16(4, 1)
    } + question + byteArrayOf(0, 0, TYPE_A.toByte(), 0, CLASS_IN.toByte())
}

private fun headerChecksum(packet: ByteArray): Int {
    var sum = 0
    for (offset in 0 until IP_HEADER step 2) sum += packet.u16(offset)
    while (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum ushr 16)
    return sum.inv() and 0xFFFF
}

private fun ByteArray.u16(offset: Int) = (this[offset].toInt() and 0xFF shl 8) or (this[offset + 1].toInt() and 0xFF)

private fun ByteArray.put16(offset: Int, value: Int) {
    this[offset] = (value ushr 8).toByte()
    this[offset + 1] = value.toByte()
}
