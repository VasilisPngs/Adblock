package com.vasilispngs.adblock.dns

import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.Origin

private const val FNV_OFFSET = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L

internal val HOST = Regex("^[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)+$")

class Rules(val allow: Set<String> = emptySet(), val block: Set<String> = emptySet())

class Verdict(val action: Action, val rule: String?, val origin: Origin)

class Blocklist(private val hashes: LongArray = LongArray(0)) {
    val size: Int get() = hashes.size

    fun contains(name: String, start: Int = 0): Boolean =
        hashes.isNotEmpty() && hashes.binarySearch(hostHash(name, start)) >= 0
}

internal fun hostHash(text: CharSequence, start: Int = 0, end: Int = text.length): Long {
    var hash = FNV_OFFSET
    for (index in start until end) hash = (hash xor text[index].code.toLong()) * FNV_PRIME
    return hash
}

internal fun ancestors(name: String): List<Int> {
    val starts = mutableListOf(0)
    var dot = name.indexOf('.')
    while (dot != -1 && dot + 1 < name.length) {
        val next = name.indexOf('.', dot + 1)
        if (next == -1) break
        starts += dot + 1
        dot = next
    }
    return starts
}

fun decide(name: String, rules: Rules, blocklist: Blocklist): Verdict {
    val starts = ancestors(name)
    if (rules.allow.isNotEmpty() || rules.block.isNotEmpty()) {
        for (start in starts) {
            val host = name.substring(start)
            if (host in rules.allow) return Verdict(Action.Allow, host, Origin.Allow)
            if (host in rules.block) return Verdict(Action.Block, host, Origin.Custom)
        }
    }
    for (start in starts) {
        if (blocklist.contains(name, start)) return Verdict(Action.Block, name.substring(start), Origin.List)
    }
    return Verdict(Action.Allow, null, Origin.None)
}
