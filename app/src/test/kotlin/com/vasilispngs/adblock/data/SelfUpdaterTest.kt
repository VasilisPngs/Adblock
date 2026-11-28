package com.vasilispngs.adblock.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class SelfUpdaterTest {
    @Test
    fun `names a file by its SHA-256 digest as GitHub does`() {
        assertEquals(
            "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".byteInputStream())
        )
    }

    @Test
    fun `hands every block it reads on`() {
        val copied = StringBuilder()

        sha256("abc".byteInputStream()) { buffer, read -> copied.append(String(buffer, 0, read)) }

        assertEquals("abc", copied.toString())
    }

    @Test
    fun `downloads only once when tapped twice`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val installed = File(context.cacheDir, "installed.apk").apply { writeText("old") }
        context.applicationInfo.sourceDir = installed.path
        val downloads = AtomicInteger()
        val releaseGate = CountDownLatch(1)
        val startedGate = CountDownLatch(1)
        val payload = "new-apk".toByteArray()
        val digest = sha256(payload.inputStream())
        val releaseJson = """
            {"assets":[{"name":"Adblock.apk","browser_download_url":"https://example.com/Adblock.apk","size":${payload.size},"digest":"$digest"}]}
        """.trimIndent().toByteArray()
        val updater = SelfUpdater(context, this) { url ->
            object : HttpURLConnection(url) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy(): Boolean = false
                override fun getResponseCode(): Int = HTTP_OK
                override fun getContentLengthLong(): Long =
                    if (url.path.endsWith(".apk")) payload.size.toLong() else releaseJson.size.toLong()
                override fun getInputStream(): InputStream =
                    if (url.path.endsWith(".apk")) {
                        downloads.incrementAndGet()
                        startedGate.countDown()
                        releaseGate.await(5, TimeUnit.SECONDS)
                        ByteArrayInputStream(ByteArray(0))
                    } else {
                        ByteArrayInputStream(releaseJson)
                    }
            }
        }

        updater.check()
        updater.status.first { it == SelfUpdate.Available }
        updater.update()
        updater.update()
        startedGate.await(5, TimeUnit.SECONDS)
        releaseGate.countDown()
        updater.status.first { it is SelfUpdate.Failed }

        assertEquals(1, downloads.get())
    }
}
