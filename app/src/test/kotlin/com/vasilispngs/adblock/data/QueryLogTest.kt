package com.vasilispngs.adblock.data

import android.database.sqlite.SQLiteFullException
import androidx.paging.PagingSource
import com.vasilispngs.adblock.ui.searchPattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QueryLogTest {
    private val database = AdblockDatabase.create(RuntimeEnvironment.getApplication())
    private val queries = database.queries()
    private val now = 1_800_000_000_000L

    @After
    fun close() = database.close()

    private fun entry(name: String, at: Long = now, action: Action = Action.Allow) =
        LoggedQuery(at = at, name = name, type = "A", action = action, origin = if (action == Action.Block) Origin.List else Origin.None, rule = null)

    private suspend fun names(action: Action? = null, pattern: String = "", since: Long = now - LOG_RETENTION_MILLIS): List<String> {
        val page = queries.page(since, action, pattern)
            .load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false))
        return (page as PagingSource.LoadResult.Page).data.map(LoggedQuery::name)
    }

    private suspend fun awaitNames(expected: List<String>, since: Long = now - LOG_RETENTION_MILLIS) = withContext(Dispatchers.Default) {
        withTimeout(WAIT_MILLIS) { while (names(since = since) != expected) delay(10) }
    }

    @Test
    fun `writes what it was given in one batch shortly after, newest first`() = runTest {
        val log = QueryLog(queries, backgroundScope) { now }
        log.add(entry("a.test", now - 2))
        log.add(entry("b.test", now - 1))
        log.add(entry("c.test", now))

        advanceTimeBy(1_001)
        runCurrent()

        awaitNames(listOf("c.test", "b.test", "a.test"))
    }

    @Test
    fun `keeps logging after a full disk lost a batch`() = runTest {
        var full = true
        val log = QueryLog(
            object : QueryDao by queries {
                override suspend fun insert(queries: List<LoggedQuery>) {
                    if (full) {
                        full = false
                        throw SQLiteFullException("database or disk is full")
                    }
                    this@QueryLogTest.queries.insert(queries)
                }
            },
            backgroundScope
        ) { now }
        log.add(entry("lost.test"))
        advanceTimeBy(1_001)
        runCurrent()

        log.add(entry("kept.test"))
        advanceTimeBy(1_001)
        runCurrent()

        awaitNames(listOf("kept.test"))
    }

    @Test
    fun `deletes entries older than a day`() = runTest {
        queries.insert(listOf(entry("old.test", now - LOG_RETENTION_MILLIS - 1)))
        val log = QueryLog(queries, backgroundScope) { now }

        log.add(entry("new.test"))
        advanceTimeBy(1_001)
        runCurrent()

        awaitNames(listOf("new.test"), since = 0)
    }

    @Test
    fun `filters by action and finds names by part`() = runTest {
        queries.insert(
            listOf(
                entry("ads.example", now - 3, Action.Block),
                entry("www.example", now - 2),
                entry("under_score.test", now - 1),
                entry("100%.test", now)
            )
        )
        assertEquals(listOf("ads.example"), names(Action.Block))
        assertEquals(listOf("100%.test", "under_score.test", "www.example"), names(Action.Allow))
        assertEquals(listOf("www.example", "ads.example"), names(pattern = "%example%"))
        assertEquals(listOf("under_score.test"), names(pattern = searchPattern("_")))
        assertEquals(listOf("100%.test"), names(pattern = searchPattern("%")))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
