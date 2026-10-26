package com.vasilispngs.adblock.dns

import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.DEFAULT_RESOLVER
import com.vasilispngs.adblock.data.LoggedQuery
import com.vasilispngs.adblock.data.Origin

private const val HEADER = 12
private const val BLOCK_TTL = 60L
private const val TTL_FLOOR = 60L
private const val TTL_CEILING = 3600L
private const val CACHE_SIZE = 4000
private const val STALE_GRACE_MILLIS = 6 * 3_600_000L
private const val STALE_TTL = 30L
private const val REFRESH_GAP_MILLIS = 5_000L
private const val MILLIS_PER_SECOND = 1_000L

class EngineState(
    val rules: Rules = Rules(),
    val blocklist: Blocklist = Blocklist(),
    val resolver: String = DEFAULT_RESOLVER,
    val logEnabled: Boolean = true
)

private class CachedAnswer(val body: ByteArray, val storedAt: Long, val expires: Long) {
    @Volatile
    var refreshedAt = 0L
}

class DnsEngine(
    private val resolver: Resolver,
    private val log: (LoggedQuery) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Volatile
    var state = EngineState()

    private val answers = LinkedHashMap<String, CachedAnswer>()

    fun handle(message: ByteArray, reply: (ByteArray) -> Unit) {
        if (message.size < HEADER) return
        val question = readQuestion(message) ?: return reply(formatError(message))
        val current = state
        val started = clock()
        val verdict = decide(question.name, current.rules, current.blocklist)
        val normalized = normalizeQuery(message, question)
        val client = normalized.client
        if (client.edns && client.version != 0) return reply(respond(badVersion(message, question), client, EXTENDED_BADVERS))
        val exchange = Exchange(message, question, client, verdict, current, started, reply)
        if (verdict.action == Action.Block) return exchange.complete(blockedResponse(message, question, BLOCK_TTL), verdict.origin)

        val key = "${current.resolver}|${question.name}|${question.type}|${question.qclass}|" +
            "${if (client.dnssecOk) 1 else 0}${if (client.checkingDisabled) 1 else 0}"
        val entry = synchronized(answers) { answers[key] }?.takeIf { it.body.size >= question.end }
        when {
            entry != null && started < entry.expires ->
                exchange.complete(replay(entry, message, question, (started - entry.storedAt) / MILLIS_PER_SECOND), Origin.Cache)
            entry != null && started < entry.expires + STALE_GRACE_MILLIS && staleTtl(entry.body) > 0 -> {
                if (started - entry.refreshedAt >= REFRESH_GAP_MILLIS) {
                    entry.refreshedAt = started
                    resolver.resolve(current.resolver, normalized.forward) { fresh ->
                        if (fresh != null && cacheable(fresh)) accept(key, fresh)
                    }
                }
                exchange.complete(replay(entry, message, question, null), Origin.Stale)
            }
            else -> resolver.resolve(current.resolver, normalized.forward) { fresh ->
                when {
                    fresh == null -> exchange.complete(servfail(message, question), verdict.origin, failed = true)
                    !cacheable(fresh) -> exchange.complete(
                        adopt(withoutOpt(fresh), message, question),
                        verdict.origin,
                        extendedRcode(fresh),
                        edeOptions(fresh)
                    )
                    else -> exchange.complete(adopt(accept(key, fresh), message, question), verdict.origin)
                }
            }
        }
    }

    private fun accept(key: String, fresh: ByteArray): ByteArray {
        val raw = withoutOpt(fresh.copyOf())
        val minimum = soaMinimum(raw)
        val answer = if (minimum == null) boostTtl(raw, TTL_FLOOR) else capTtl(raw, minimum)
        val life = ttl(answer)
        val now = clock()
        synchronized(answers) {
            if (answers.size >= CACHE_SIZE) answers.remove(answers.keys.first())
            answers.remove(key)
            answers[key] = CachedAnswer(answer, now, now + life * MILLIS_PER_SECOND)
        }
        return answer
    }

    private inner class Exchange(
        private val message: ByteArray,
        private val question: Question,
        private val client: Client,
        private val verdict: Verdict,
        private val state: EngineState,
        private val started: Long,
        private val reply: (ByteArray) -> Unit
    ) {
        fun complete(
            body: ByteArray,
            origin: Origin,
            extended: Int = 0,
            options: List<ByteArray> = emptyList(),
            failed: Boolean = false
        ) {
            val cloaked = if (verdict.action == Action.Block || verdict.origin == Origin.Allow) null else cloakedBy(body)
            if (!failed && state.logEnabled) {
                log(
                    LoggedQuery(
                        at = started,
                        name = question.name,
                        type = QUERY_TYPES[question.type] ?: question.type.toString(),
                        action = if (cloaked != null) Action.Block else verdict.action,
                        origin = if (cloaked != null) Origin.Cname else origin,
                        rule = cloaked?.rule ?: verdict.rule
                    )
                )
            }
            reply(
                if (cloaked != null) {
                    respond(blockedResponse(message, question, BLOCK_TTL), client)
                } else {
                    respond(body, client, extended, options)
                }
            )
        }

        private fun cloakedBy(body: ByteArray): Verdict? = cnameTargets(body)
            .map { decide(it, state.rules, state.blocklist) }
            .firstOrNull { it.action == Action.Block }
    }
}

private fun ttl(body: ByteArray): Long {
    val ttl = minimumTtl(body)
    val minimum = soaMinimum(body)
    return if (minimum == null) ttl.coerceIn(TTL_FLOOR, TTL_CEILING) else minOf(ttl, minimum, TTL_CEILING)
}

private fun staleTtl(body: ByteArray): Long = soaMinimum(body)?.let { minOf(STALE_TTL, it) } ?: STALE_TTL

private fun adopt(source: ByteArray, message: ByteArray, question: Question): ByteArray {
    if (source.size < question.end) return source
    return source.copyOf().also { body ->
        body[0] = message[0]
        body[1] = message[1]
        message.copyInto(body, HEADER, HEADER, question.end)
    }
}

private fun replay(entry: CachedAnswer, message: ByteArray, question: Question, age: Long?): ByteArray {
    val body = adopt(entry.body, message, question)
    return if (age == null) setTtl(body, staleTtl(entry.body)) else decrementTtl(body, age)
}
