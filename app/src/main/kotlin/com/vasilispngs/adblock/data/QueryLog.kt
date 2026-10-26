package com.vasilispngs.adblock.data

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val LOG_RETENTION_MILLIS = 86_400_000L
private const val FLUSH_DELAY_MILLIS = 1_000L
private const val CLEANUP_INTERVAL_MILLIS = 3_600_000L

class QueryLog(
    private val queries: QueryDao,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val pending = Channel<LoggedQuery>(Channel.UNLIMITED)
    private var cleanedAt = 0L

    init {
        scope.launch {
            while (true) {
                val batch = mutableListOf(pending.receive())
                delay(FLUSH_DELAY_MILLIS)
                while (true) batch += pending.tryReceive().getOrNull() ?: break
                try {
                    queries.insert(batch)
                    val now = clock()
                    if (now - cleanedAt >= CLEANUP_INTERVAL_MILLIS) {
                        cleanedAt = now
                        queries.deleteBefore(now - LOG_RETENTION_MILLIS)
                    }
                } catch (_: SQLiteException) {
                }
            }
        }
    }

    fun add(query: LoggedQuery) {
        pending.trySend(query)
    }
}
