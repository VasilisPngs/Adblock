package com.vasilispngs.adblock.data

import com.vasilispngs.adblock.dns.hostHash
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class BlocklistsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val directory = File(context.filesDir, "lists")
    private val database = AdblockDatabase.create(context)
    private val served = mutableMapOf<String, Response>()
    private val requests = mutableListOf<Pair<String, Map<String, String>>>()
    private var downloads = 0
    private var now = 1_800_000_000_000L

    private class Response(val code: Int, val body: String = "", val headers: Map<String, String> = emptyMap())

    private inner class FakeConnection(url: URL) : HttpURLConnection(url) {
        private val properties = mutableMapOf<String, String>()
        private var recorded = false
        private val response get() = served[url.toString()] ?: throw IOException("Offline")

        override fun setRequestProperty(key: String, value: String) {
            properties[key] = value
        }

        override fun getResponseCode(): Int {
            if (!recorded) {
                recorded = true
                requests += url.toString() to properties.toMap()
            }
            return response.code
        }

        override fun getInputStream(): InputStream = ByteArrayInputStream(response.body.toByteArray())

        override fun getHeaderField(name: String): String? = response.headers[name]

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy() = false
    }

    private val blocklists = Blocklists(
        directory = directory,
        sources = database.sources(),
        open = { FakeConnection(it) },
        downloadNew = { downloads += 1 },
        clock = { now }
    )

    @After
    fun close() = database.close()

    @Test
    fun `reads hosts files and plain adblock domain rules`() {
        val hosts = listOf(
            "0.0.0.0 ads.example",
            "127.0.0.1\ttracker.test",
            ":: v6.test",
            "::1 loop.test",
            "||metrics.example.net^",
            "plain.test",
            "  Mixed.CASE.test.  ",
            "under_score.test\r"
        ).mapNotNull(::listHost)
        assertEquals(
            listOf("ads.example", "loop.test", "metrics.example.net", "mixed.case.test", "plain.test", "tracker.test", "under_score.test", "v6.test"),
            hosts.sorted()
        )
    }

    @Test
    fun `skips comments, headers, exceptions, modifiers, rewrites, wildcards and single labels`() {
        val lines = listOf(
            "! Title: Example",
            "# comment",
            "[Adblock Plus 2.0]",
            "",
            "@@||allowed.test^",
            "||third.test^\$third-party",
            "||rewrite.test^\$dnsrewrite=NOERROR;A;1.2.3.4",
            "||important.test^\$important",
            "*.wild.test",
            "||*.wild.test^",
            "/regex\\.test/",
            "localhost",
            "0.0.0.0 localhost",
            "example.test/path",
            "-bad.test",
            "bad-.test"
        )
        assertTrue(lines.mapNotNull(::listHost).isEmpty())
    }

    @Test
    fun `reads the title and version from the list header`() {
        val parsed = parseList("! Title: Example list\n! Version: 2026.1026.0933.09\n||ads.example^\n||ads.example^\n".reader().buffered())
        assertEquals("Example list", parsed.title)
        assertEquals("2026.1026.0933.09", parsed.version)
        assertEquals(1, parsed.hashes.size)
        assertEquals("27 Sep 2026 08:52 UTC", parseList("# Last modified: 27 Sep 2026 08:52 UTC\n".reader().buffered()).version)
        assertEquals("2026-09-27", parseList("! Updated: 2026-09-27\n".reader().buffered()).version)
        assertNull(parseList("0.0.0.0 a.test\n".reader().buffered()).version)
        assertEquals("", parseList("! Title: ${"x".repeat(121)}\n".reader().buffered()).title)
    }

    @Test
    fun `starts with HaGeZi's Multi PRO mini and downloads it with its title`() = runTest {
        val source = database.sources().all().single()
        assertEquals(DEFAULT_LIST_URL, source.url)
        assertNull(source.domains)
        served[DEFAULT_LIST_URL] = Response(200, "[Adblock Plus]\n! Title: HaGeZi\n! Version: 1\n||ads.example^\n||tracker.test^\n", mapOf("ETag" to "W/\"1\""))

        assertTrue(blocklists.update(missingOnly = true))

        val updated = database.sources().all().single()
        assertEquals(2, updated.domains)
        assertEquals("1", updated.version)
        assertEquals("W/\"1\"", updated.etag)
        assertEquals(now, updated.updatedAt)
        assertEquals(DEFAULT_LIST_NAME, updated.name)
        val list = blocklists.current.first()
        assertTrue(list.contains("ads.example"))
        assertTrue(list.contains("tracker.test"))
        assertFalse(list.contains("example.test"))
    }

    @Test
    fun `asks again only with the validators it was given and keeps the list when nothing changed`() = runTest {
        served[DEFAULT_LIST_URL] = Response(200, "||ads.example^\n", mapOf("ETag" to "W/\"1\""))
        blocklists.update(missingOnly = false)
        served[DEFAULT_LIST_URL] = Response(304)
        now += 86_400_000L

        assertTrue(blocklists.update(missingOnly = false))

        assertEquals(mapOf("If-None-Match" to "W/\"1\""), requests.last().second)
        val source = database.sources().all().single()
        assertEquals(1, source.domains)
        assertEquals(now, source.updatedAt)
        assertTrue(blocklists.current.first().contains("ads.example"))
    }

    @Test
    fun `skips lists it already has when only new ones are wanted`() = runTest {
        served[DEFAULT_LIST_URL] = Response(200, "||ads.example^\n")
        blocklists.update(missingOnly = true)
        blocklists.update(missingOnly = true)
        assertEquals(1, requests.size)
        assertFalse(blocklists.missing())
    }

    @Test
    fun `marks a failed download and asks to retry`() = runTest {
        assertFalse(blocklists.update(missingOnly = true))
        val source = database.sources().all().single()
        assertEquals(now, source.failedAt)
        assertNull(source.domains)
        assertTrue(blocklists.missing())

        served[DEFAULT_LIST_URL] = Response(500)
        assertFalse(blocklists.update(missingOnly = true))
    }

    @Test
    fun `adds a list, names it from its title and merges it with the others`() = runTest {
        served[DEFAULT_LIST_URL] = Response(200, "||ads.example^\n")
        blocklists.update(missingOnly = true)
        blocklists.add(OTHER, "")
        assertEquals(1, downloads)
        served[OTHER] = Response(200, "! Title: Other list\n0.0.0.0 tracker.test\n0.0.0.0 ads.example\n")

        blocklists.update(missingOnly = true)

        assertEquals(listOf(DEFAULT_LIST_URL, OTHER), requests.map { it.first })
        assertEquals("Other list", database.sources().get(OTHER)!!.name)
        val list = blocklists.current.first()
        assertEquals(2, list.size)
        assertTrue(list.contains("tracker.test"))
    }

    @Test
    fun `adding a list again keeps it and only renames it`() = runTest {
        served[DEFAULT_LIST_URL] = Response(200, "||ads.example^\n")
        blocklists.update(missingOnly = true)

        blocklists.add(DEFAULT_LIST_URL, "Mine")
        blocklists.add(DEFAULT_LIST_URL, "")

        val source = database.sources().all().single()
        assertEquals("Mine", source.name)
        assertEquals(1, source.domains)
        assertEquals(0, downloads)
    }

    @Test
    fun `removing a list stops blocking its domains at once`() = runTest {
        served[DEFAULT_LIST_URL] = Response(200, "||ads.example^\n")
        blocklists.update(missingOnly = true)
        assertTrue(blocklists.current.first().contains("ads.example"))

        blocklists.remove(DEFAULT_LIST_URL)

        assertTrue(database.sources().all().isEmpty())
        assertFalse(blocklists.current.first().contains("ads.example"))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `keeps the merged list sorted and unique`() {
        val merged = unique(longArrayOf(5, 1, 5, 3, 1))
        assertEquals(listOf(1L, 3L, 5L), merged.toList())
        assertNotNull(hostHash("ads.example"))
    }

    private companion object {
        const val OTHER = "https://lists.example/other.txt"
    }
}
