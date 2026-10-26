package com.vasilispngs.adblock.dns

internal object Type {
    const val A = 1
    const val CNAME = 5
    const val SOA = 6
    const val TXT = 16
    const val AAAA = 28
    const val OPT = 41
    const val HTTPS = 65
}

internal class TestRecord(
    val type: Int,
    val data: String = "",
    val ttl: Long = 300,
    val name: String? = null,
    val raw: ByteArray? = null,
    val minimum: Long = 300
)

internal class Option(val code: Int, val length: Int)

internal class Opt(val udp: Int, val extended: Int, val version: Int, val dnssecOk: Boolean, val options: List<Option>)

internal class ParsedRecord(val name: String, val type: Int, val ttl: Long, val data: Any?)

internal class ParsedQuestion(val name: String, val type: Int, val qclass: Int)

internal class Parsed(
    val id: Int,
    val qr: Boolean,
    val opcode: Int,
    val rd: Boolean,
    val ra: Boolean,
    val rcode: Int,
    val counts: List<Int>,
    val question: ParsedQuestion,
    val answers: List<ParsedRecord>,
    val authority: List<ParsedRecord>,
    val additional: List<ParsedRecord>,
    val opt: Opt?
)

private fun u16(value: Int) = listOf((value shr 8 and 0xFF).toByte(), (value and 0xFF).toByte())

private fun u32(value: Long) = u16((value ushr 16).toInt()) + u16(value.toInt() and 0xFFFF)

private fun encodeName(name: String): List<Byte> =
    name.split('.').filter(String::isNotEmpty).flatMap { label -> listOf(label.length.toByte()) + label.toByteArray().toList() } + 0.toByte()

private fun optRecord(version: Int = 0, dnssecOk: Boolean = false, options: List<Pair<Int, List<Byte>>> = emptyList()): List<Byte> {
    val data = options.flatMap { (code, bytes) -> u16(code) + u16(bytes.size) + bytes }
    return listOf<Byte>(0) + u16(Type.OPT) + u16(1232) + listOf<Byte>(0, version.toByte()) + u16(if (dnssecOk) 0x8000 else 0) + u16(data.size) + data
}

internal fun query(
    name: String,
    type: Int = Type.A,
    id: Int = 0x1234,
    edns: Boolean = false,
    dnssecOk: Boolean = false,
    checkingDisabled: Boolean = false,
    padding: Boolean = false,
    version: Int = 0,
    questions: Int = 1
): ByteArray {
    val options = if (padding) listOf(12 to List<Byte>(8) { 0 }) else emptyList()
    return (
        u16(id) + listOf<Byte>(0x01, if (checkingDisabled) 0x10 else 0) + u16(questions) + List<Byte>(4) { 0 } +
            u16(if (edns) 1 else 0) + encodeName(name) + u16(type) + u16(1) +
            (if (edns) optRecord(version, dnssecOk, options) else emptyList())
        ).toByteArray()
}

private fun rdata(record: TestRecord): List<Byte> = when {
    record.raw != null -> record.raw.toList()
    record.type == Type.A -> record.data.split('.').map { it.toInt().toByte() }
    record.type == Type.AAAA -> List<Byte>(15) { 0 } + 1.toByte()
    record.type == Type.CNAME -> encodeName(record.data)
    record.type == Type.SOA -> listOf<Byte>(0, 0) + u32(1) + u32(3600) + u32(600) + u32(86400) + u32(record.minimum)
    else -> record.data.toByteArray().toList()
}

private fun encodeRecord(record: TestRecord): List<Byte> {
    val owner = record.name?.let(::encodeName) ?: listOf(0xC0.toByte(), 0x0C)
    val data = rdata(record)
    return owner + u16(record.type) + u16(1) + u32(record.ttl) + u16(data.size) + data
}

internal fun answer(
    message: ByteArray,
    rcode: Int = 0,
    answers: List<TestRecord> = emptyList(),
    authority: List<TestRecord> = emptyList(),
    additional: List<TestRecord> = emptyList(),
    id: Int? = null,
    ede: Int? = null
): ByteArray {
    val asked = parse(message)
    var end = 12
    while (message[end].toInt() != 0) end += message[end] + 1
    end += 5
    val withOpt = (message[10].toInt() and 0xFF shl 8 or (message[11].toInt() and 0xFF)) > 0
    val options = ede?.let { listOf(15 to u16(it) + "upstream".toByteArray().toList()) } ?: emptyList()
    return (
        u16(id ?: asked.id) +
            listOf<Byte>((0x80 or (message[2].toInt() and 0x79)).toByte(), (0x80 or rcode).toByte(), 0, 1) +
            u16(answers.size) + u16(authority.size) + u16(additional.size + if (withOpt) 1 else 0) +
            message.copyOfRange(12, end).toList() +
            answers.flatMap(::encodeRecord) + authority.flatMap(::encodeRecord) + additional.flatMap(::encodeRecord) +
            (if (withOpt) optRecord(dnssecOk = asked.opt?.dnssecOk == true, options = options) else emptyList())
        ).toByteArray()
}

private fun ByteArray.at16(offset: Int) = (this[offset].toInt() and 0xFF shl 8) or (this[offset + 1].toInt() and 0xFF)

private fun ByteArray.at32(offset: Int) = (at16(offset).toLong() shl 16) or at16(offset + 2).toLong()

private fun readName(bytes: ByteArray, start: Int): Pair<String, Int> {
    val labels = mutableListOf<String>()
    var offset = start
    var end = -1
    repeat(20) {
        val length = bytes[offset].toInt() and 0xFF
        if (length == 0) return labels.joinToString(".") to (if (end < 0) offset + 1 else end)
        if (length and 0xC0 == 0xC0) {
            if (end < 0) end = offset + 2
            offset = (length and 0x3F shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        } else {
            labels += String(bytes, offset + 1, length, Charsets.ISO_8859_1)
            offset += length + 1
        }
    }
    error("name loop")
}

internal fun parse(bytes: ByteArray): Parsed {
    val counts = listOf(4, 6, 8, 10).map(bytes::at16)
    val (questionName, questionEnd) = readName(bytes, 12)
    var offset = questionEnd + 4
    val sections = List(3) { mutableListOf<ParsedRecord>() }
    var opt: Opt? = null
    counts.drop(1).forEachIndexed { index, count ->
        repeat(count) {
            val (owner, ownerEnd) = readName(bytes, offset)
            offset = ownerEnd
            val type = bytes.at16(offset)
            val ttl = bytes.at32(offset + 4)
            val length = bytes.at16(offset + 8)
            val start = offset + 10
            offset = start + length
            if (type == Type.OPT) {
                val options = mutableListOf<Option>()
                var cursor = start
                while (cursor + 4 <= offset) {
                    options += Option(bytes.at16(cursor), bytes.at16(cursor + 2))
                    cursor += 4 + bytes.at16(cursor + 2)
                }
                opt = Opt(
                    udp = bytes.at16(start - 8),
                    extended = bytes[start - 6].toInt() and 0xFF,
                    version = bytes[start - 5].toInt() and 0xFF,
                    dnssecOk = bytes.at16(start - 4) and 0x8000 != 0,
                    options = options
                )
            } else {
                val data: Any? = when (type) {
                    Type.A -> bytes.copyOfRange(start, offset).joinToString(".") { (it.toInt() and 0xFF).toString() }
                    Type.AAAA -> bytes.copyOfRange(start, offset).map { it.toInt() and 0xFF }
                    Type.CNAME -> readName(bytes, start).first
                    Type.SOA -> bytes.at32(offset - 4)
                    else -> null
                }
                sections[index] += ParsedRecord(owner, type, ttl, data)
            }
        }
    }
    return Parsed(
        id = bytes.at16(0),
        qr = bytes[2].toInt() and 0x80 != 0,
        opcode = bytes[2].toInt() shr 3 and 0x0F,
        rd = bytes[2].toInt() and 0x01 != 0,
        ra = bytes[3].toInt() and 0x80 != 0,
        rcode = bytes[3].toInt() and 0x0F,
        counts = counts,
        question = ParsedQuestion(questionName, bytes.at16(questionEnd), bytes.at16(questionEnd + 2)),
        answers = sections[0],
        authority = sections[1],
        additional = sections[2],
        opt = opt
    )
}
