package com.vasilispngs.adblock.dns

internal const val TYPE_A = 1
internal const val TYPE_AAAA = 28
private const val TYPE_CNAME = 5
private const val TYPE_SOA = 6
private const val TYPE_OPT = 41
private const val CLASS_IN = 1
private const val HEADER = 12
private const val SOA_RDLENGTH = 24
private const val OPT_LENGTH = 11
private const val MAX_LABEL = 63
private const val MAX_JUMPS = 8
private const val FLAG_CD = 0x10
private const val FLAG_DO = 0x8000
private const val OPTION_PADDING = 12
private const val OPTION_EDE = 15
private const val PADDING_BLOCK = 468
private const val RESPONSE_PAYLOAD = 1232
private const val MAX_MESSAGE = 0xFFFF
private const val RCODE_FORMERR = 1
private const val RCODE_SERVFAIL = 2
private const val RCODE_NXDOMAIN = 3
private const val POINTER = 0xC0
private const val QUESTION_POINTER = 0xC00C
private const val SOA_SERIAL = 1L
private const val SOA_REFRESH = 3600L
private const val SOA_RETRY = 600L
private const val SOA_EXPIRE = 86400L
private const val ANSWERS = 1
private const val WITH_AUTHORITY = 2
private const val ALL_RECORDS = 3
internal const val EXTENDED_BADVERS = 1

internal val QUERY_TYPES = mapOf(
    1 to "A",
    2 to "NS",
    5 to "CNAME",
    6 to "SOA",
    12 to "PTR",
    15 to "MX",
    16 to "TXT",
    28 to "AAAA",
    33 to "SRV",
    35 to "NAPTR",
    43 to "DS",
    48 to "DNSKEY",
    64 to "SVCB",
    65 to "HTTPS",
    257 to "CAA"
)

internal class Question(val name: String, val type: Int, val qclass: Int, val end: Int)

internal class Client(
    val edns: Boolean,
    val version: Int,
    val dnssecOk: Boolean,
    val checkingDisabled: Boolean,
    val padding: Boolean
)

internal class Normalized(val forward: ByteArray, val client: Client)

internal fun readQuestion(message: ByteArray): Question? {
    if (message.size < HEADER + 1 || message.u16(4) != 1) return null
    val name = StringBuilder()
    var offset = HEADER
    while (offset < message.size) {
        val length = message.u8(offset)
        if (length == 0) {
            offset += 1
            break
        }
        if (length > MAX_LABEL || offset + length + 1 > message.size) return null
        if (name.isNotEmpty()) name.append('.')
        for (index in offset + 1..offset + length) name.append(message.u8(index).toChar().lowercaseChar())
        offset += length + 1
    }
    if (name.isEmpty() || offset + 4 > message.size) return null
    return Question(name.toString(), message.u16(offset), message.u16(offset + 2), offset + 4)
}

internal fun blockedResponse(message: ByteArray, question: Question, ttl: Long): ByteArray {
    val questionLength = question.end - HEADER
    val address = when (question.type) {
        TYPE_A -> 4
        TYPE_AAAA -> 16
        else -> 0
    }
    val rdlength = if (address > 0) address else SOA_RDLENGTH
    val response = ByteArray(HEADER + questionLength + 12 + rdlength)
    message.copyInto(response, 0, 0, 2)
    response[2] = (0x80 or (message.u8(2) and 0x79)).toByte()
    response[3] = 0x80.toByte()
    response.put16(4, 1)
    response.put16(6, if (address > 0) 1 else 0)
    response.put16(8, if (address > 0) 0 else 1)
    message.copyInto(response, HEADER, HEADER, question.end)
    val offset = HEADER + questionLength
    response.put16(offset, QUESTION_POINTER)
    response.put16(offset + 2, if (address > 0) question.type else TYPE_SOA)
    response.put16(offset + 4, question.qclass)
    response.put32(offset + 6, ttl)
    response.put16(offset + 10, rdlength)
    if (address > 0) return response
    response.put16(offset + 12, QUESTION_POINTER)
    response.put16(offset + 14, QUESTION_POINTER)
    response.put32(offset + 16, SOA_SERIAL)
    response.put32(offset + 20, SOA_REFRESH)
    response.put32(offset + 24, SOA_RETRY)
    response.put32(offset + 28, SOA_EXPIRE)
    response.put32(offset + 32, ttl)
    return response
}

private fun bareResponse(message: ByteArray, question: Question, rcode: Int): ByteArray =
    message.copyOf(question.end).apply {
        this[2] = (0x80 or (message.u8(2) and 0x79)).toByte()
        this[3] = (0x80 or rcode).toByte()
        put16(6, 0)
        put16(8, 0)
        put16(10, 0)
    }

internal fun servfail(message: ByteArray, question: Question): ByteArray = bareResponse(message, question, RCODE_SERVFAIL)

internal fun badVersion(message: ByteArray, question: Question): ByteArray = bareResponse(message, question, 0)

internal fun formatError(message: ByteArray): ByteArray = message.copyOf(HEADER).apply {
    this[2] = (0x80 or (message.u8(2) and 0x79)).toByte()
    this[3] = (0x80 or RCODE_FORMERR).toByte()
    put16(4, 0)
    put16(6, 0)
    put16(8, 0)
    put16(10, 0)
}

internal fun normalizeQuery(message: ByteArray, question: Question): Normalized {
    val at = question.end
    var edns = false
    var version = 0
    var dnssecOk = false
    var padding = false
    if (message.u16(10) > 0 && at + OPT_LENGTH <= message.size && message[at].toInt() == 0 && message.u16(at + 1) == TYPE_OPT) {
        edns = true
        version = message.u8(at + 6)
        dnssecOk = message.u16(at + 7) and FLAG_DO != 0
        val rdataEnd = minOf(message.size, at + OPT_LENGTH + message.u16(at + 9))
        var cursor = at + OPT_LENGTH
        while (cursor + 4 <= rdataEnd) {
            val length = message.u16(cursor + 2)
            if (cursor + 4 + length > rdataEnd) break
            if (message.u16(cursor) == OPTION_PADDING) padding = true
            cursor += 4 + length
        }
    }
    val forward = ByteArray(at + OPT_LENGTH)
    message.copyInto(forward, 0, 0, at)
    forward.put16(6, 0)
    forward.put16(8, 0)
    forward.put16(10, 1)
    forward.put16(at + 1, TYPE_OPT)
    forward.put16(at + 3, RESPONSE_PAYLOAD)
    forward.put32(at + 5, if (dnssecOk) FLAG_DO.toLong() else 0L)
    forward.put16(at + 9, 0)
    return Normalized(forward, Client(edns, version, dnssecOk, message.u8(3) and FLAG_CD != 0, padding))
}

private fun readName(message: ByteArray, start: Int): String {
    val name = StringBuilder()
    var offset = start
    var jumps = 0
    while (offset < message.size) {
        val length = message.u8(offset)
        if (length == 0) return name.toString()
        if (length and POINTER == POINTER) {
            if (offset + 1 >= message.size || jumps > MAX_JUMPS) return ""
            offset = (length and 0x3F shl 8) or message.u8(offset + 1)
            jumps += 1
            continue
        }
        if (length > MAX_LABEL || offset + length + 1 > message.size) return ""
        if (name.isNotEmpty()) name.append('.')
        for (index in offset + 1..offset + length) name.append(message.u8(index).toChar().lowercaseChar())
        offset += length + 1
    }
    return ""
}

private inline fun walkRecords(message: ByteArray, sections: Int, visit: (type: Int, offset: Int, length: Int) -> Unit) {
    val question = readQuestion(message) ?: return
    val records = message.u16(6) + (if (sections > ANSWERS) message.u16(8) else 0) + (if (sections > WITH_AUTHORITY) message.u16(10) else 0)
    var offset = question.end
    var index = 0
    while (index < records && offset + 11 <= message.size) {
        while (offset < message.size) {
            val length = message.u8(offset)
            if (length == 0) {
                offset += 1
                break
            }
            if (length and POINTER == POINTER) {
                offset += 2
                break
            }
            offset += length + 1
        }
        if (offset + 10 > message.size) return
        val type = message.u16(offset)
        val length = message.u16(offset + 8)
        if (offset + 10 + length > message.size) return
        visit(type, offset, length)
        offset += 10 + length
        index += 1
    }
}

internal fun cnameTargets(message: ByteArray): List<String> {
    val targets = mutableListOf<String>()
    walkRecords(message, ANSWERS) { type, offset, _ ->
        if (type == TYPE_CNAME) readName(message, offset + 10).takeIf(String::isNotEmpty)?.let(targets::add)
    }
    return targets
}

private inline fun rewriteTtl(message: ByteArray, change: (Long) -> Long): ByteArray {
    walkRecords(message, ALL_RECORDS) { type, offset, _ ->
        if (type != TYPE_OPT) message.put32(offset + 4, change(message.u32(offset + 4)))
    }
    return message
}

internal fun decrementTtl(message: ByteArray, seconds: Long): ByteArray =
    if (seconds > 0) rewriteTtl(message) { ttl -> if (ttl > seconds) ttl - seconds else 1 } else message

internal fun setTtl(message: ByteArray, ttl: Long): ByteArray = rewriteTtl(message) { ttl }

internal fun boostTtl(message: ByteArray, floor: Long): ByteArray = rewriteTtl(message) { ttl -> maxOf(ttl, floor) }

internal fun capTtl(message: ByteArray, ceiling: Long): ByteArray = rewriteTtl(message) { ttl -> minOf(ttl, ceiling) }

internal fun soaMinimum(message: ByteArray): Long? {
    if (message.size < HEADER || message.u16(6) > 0) return null
    var minimum: Long? = null
    walkRecords(message, WITH_AUTHORITY) { type, offset, length ->
        if (type == TYPE_SOA && length >= 22 && minimum == null) minimum = message.u32(offset + 6 + length)
    }
    return minimum
}

internal fun minimumTtl(message: ByteArray): Long {
    var ttl = Long.MAX_VALUE
    walkRecords(message, ANSWERS) { _, offset, _ -> ttl = minOf(ttl, message.u32(offset + 4)) }
    if (ttl != Long.MAX_VALUE) return ttl
    walkRecords(message, WITH_AUTHORITY) { type, offset, length ->
        if (type == TYPE_SOA && length >= 22) ttl = minOf(ttl, message.u32(offset + 4), message.u32(offset + 6 + length))
    }
    return if (ttl != Long.MAX_VALUE) ttl else 0
}

private fun opcode(message: ByteArray) = message.u8(2) shr 3 and 0x0F

internal fun answersQuery(query: ByteArray, response: ByteArray): Boolean {
    if (response.size < HEADER || response.u8(2) and 0x80 == 0) return false
    if (response[0] != query[0] || response[1] != query[1] || opcode(response) != opcode(query)) return false
    val asked = readQuestion(query) ?: return false
    val answered = readQuestion(response) ?: return false
    return asked.name == answered.name && asked.type == answered.type && asked.qclass == answered.qclass
}

private fun optTypeOffset(message: ByteArray): Int {
    var found = -1
    walkRecords(message, ALL_RECORDS) { type, offset, _ ->
        if (type == TYPE_OPT && found < 0) found = offset
    }
    return found
}

internal fun extendedRcode(message: ByteArray): Int {
    val opt = optTypeOffset(message)
    return if (opt < 0) 0 else message.u8(opt + 4)
}

internal fun edeOptions(message: ByteArray): List<ByteArray> {
    val opt = optTypeOffset(message)
    if (opt < 0) return emptyList()
    val rdataEnd = opt + 10 + message.u16(opt + 8)
    val found = mutableListOf<ByteArray>()
    var cursor = opt + 10
    while (cursor + 4 <= rdataEnd) {
        val length = message.u16(cursor + 2)
        if (cursor + 4 + length > rdataEnd) break
        if (message.u16(cursor) == OPTION_EDE) found += message.copyOfRange(cursor, cursor + 4 + length)
        cursor += 4 + length
    }
    return found
}

internal fun withoutOpt(message: ByteArray): ByteArray {
    val opt = optTypeOffset(message)
    if (opt < 1 || message[opt - 1].toInt() != 0) return message
    val start = opt - 1
    val end = opt + 10 + message.u16(opt + 8)
    val out = ByteArray(message.size - (end - start))
    message.copyInto(out, 0, 0, start)
    message.copyInto(out, start, end, message.size)
    out.put16(10, out.u16(10) - 1)
    return out
}

internal fun respond(body: ByteArray, client: Client, extended: Int = 0, options: List<ByteArray> = emptyList()): ByteArray {
    if (!client.edns) return body
    val optionBytes = options.sumOf(ByteArray::size)
    var size = body.size + OPT_LENGTH + optionBytes
    if (size > MAX_MESSAGE) return body
    var fill = -1
    if (client.padding) {
        val target = minOf(MAX_MESSAGE, (size + 4 + PADDING_BLOCK - 1) / PADDING_BLOCK * PADDING_BLOCK)
        if (target >= size + 4) {
            fill = target - size - 4
            size = target
        }
    }
    val out = ByteArray(size)
    body.copyInto(out)
    out.put16(10, out.u16(10) + 1)
    var at = body.size
    out.put16(at + 1, TYPE_OPT)
    out.put16(at + 3, RESPONSE_PAYLOAD)
    out[at + 5] = extended.toByte()
    out.put16(at + 7, if (client.dnssecOk) FLAG_DO else 0)
    out.put16(at + 9, optionBytes + if (fill >= 0) 4 + fill else 0)
    at += OPT_LENGTH
    for (option in options) {
        option.copyInto(out, at)
        at += option.size
    }
    if (fill >= 0) {
        out.put16(at, OPTION_PADDING)
        out.put16(at + 2, fill)
    }
    return out
}

internal fun rcode(message: ByteArray): Int = message.u8(3) and 0x0F

internal fun cacheable(message: ByteArray): Boolean =
    (rcode(message) == 0 || rcode(message) == RCODE_NXDOMAIN) && extendedRcode(message) == 0

internal fun addressQuery(name: String): ByteArray {
    val labels = name.trimEnd('.').split('.').map(String::toByteArray)
    require(labels.all { it.size in 1..MAX_LABEL })
    val question = labels.fold(byteArrayOf()) { bytes, label -> bytes + label.size.toByte() + label }
    return ByteArray(HEADER).apply {
        put16(2, 0x0100)
        put16(4, 1)
    } + question + byteArrayOf(0, 0, TYPE_A.toByte(), 0, CLASS_IN.toByte())
}

internal fun ByteArray.u8(offset: Int) = this[offset].toInt() and 0xFF

internal fun ByteArray.u16(offset: Int) = (u8(offset) shl 8) or u8(offset + 1)

internal fun ByteArray.u32(offset: Int): Long = (u16(offset).toLong() shl 16) or u16(offset + 2).toLong()

internal fun ByteArray.put16(offset: Int, value: Int) {
    this[offset] = (value ushr 8).toByte()
    this[offset + 1] = value.toByte()
}

internal fun ByteArray.put32(offset: Int, value: Long) {
    put16(offset, (value ushr 16).toInt())
    put16(offset + 2, value.toInt())
}
