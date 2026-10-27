package com.vasilispngs.adblock.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vasilispngs.adblock.appContainer
import com.vasilispngs.adblock.dns.Blocklist
import com.vasilispngs.adblock.dns.HOST
import com.vasilispngs.adblock.dns.hostHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

private const val HEAD_LENGTH = 4096
private const val MAX_TITLE = 120
private const val INITIAL_CAPACITY = 1024
private const val READ_SIZE = 8192
private const val MAX_LINE = 4096
private const val MAX_CHARACTERS = 128L shl 20
private const val MAX_DOMAINS = 4_000_000
private const val TIMEOUT_MILLIS = 60_000
private const val DAILY_WORK = "blocklists"
private const val NEW_LISTS_WORK = "new-blocklists"
private const val MISSING_ONLY = "missing_only"

private val HOSTS_PREFIX = Regex("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1?)\\s+")
private val WHITESPACE = Regex("\\s+")
private val TITLE = Regex("^[!#]\\s*Title:\\s*(.+)$", RegexOption.MULTILINE)
private val VERSION = Regex("^[!#]\\s*Version:\\s*(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
private val MODIFIED = Regex(
    "^[!#]\\s*(?:Last modified|Last updated|Updated):\\s*(.+)$",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
)
private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

internal class ParsedList(val hashes: LongArray, val title: String, val version: String?)

internal fun listHost(raw: String): String? {
    val line = raw.trim()
    if (line.isEmpty() || line[0] == '!' || line[0] == '#' || line[0] == '[') return null
    var body = (if (line.startsWith("||")) line.substring(2) else line).removeSuffix("^")
    if (HOSTS_PREFIX.containsMatchIn(body)) body = body.split(WHITESPACE).getOrElse(1) { "" }
    return body.lowercase().removeSuffix(".").takeIf(HOST::matches)
}

internal fun parseList(reader: Reader): ParsedList {
    var hashes = LongArray(INITIAL_CAPACITY)
    var count = 0
    var total = 0L
    var skipping = false
    val head = StringBuilder()
    val line = StringBuilder()
    val buffer = CharArray(READ_SIZE)
    fun endLine() {
        if (!skipping) {
            if (head.length < HEAD_LENGTH) head.append(line).append('\n')
            listHost(line.toString())?.let { host ->
                if (count == MAX_DOMAINS) throw IOException("More than $MAX_DOMAINS domains")
                if (count == hashes.size) hashes = hashes.copyOf(count * 2)
                hashes[count++] = hostHash(host)
            }
        }
        line.setLength(0)
        skipping = false
    }
    while (true) {
        val read = reader.read(buffer)
        if (read < 0) break
        total += read
        if (total > MAX_CHARACTERS) throw IOException("More than $MAX_CHARACTERS characters")
        for (index in 0 until read) {
            val char = buffer[index]
            when {
                char == '\n' -> endLine()
                skipping -> Unit
                line.length == MAX_LINE -> skipping = true
                else -> line.append(char)
            }
        }
    }
    if (line.isNotEmpty() || skipping) endLine()
    val text = head.take(HEAD_LENGTH)
    val title = TITLE.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.length <= MAX_TITLE }.orEmpty()
    val version = (VERSION.find(text) ?: MODIFIED.find(text))?.groupValues?.get(1)?.trim()
    return ParsedList(unique(hashes.copyOf(count)), title, version)
}

internal fun unique(hashes: LongArray): LongArray {
    hashes.sort()
    var count = 0
    for (hash in hashes) {
        if (count == 0 || hashes[count - 1] != hash) hashes[count++] = hash
    }
    return hashes.copyOf(count)
}

internal fun scheduleDailyLists(context: Context) {
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        DAILY_WORK,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.DAYS).setConstraints(network).build()
    )
}

internal fun downloadNewLists(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        NEW_LISTS_WORK,
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network).setInputData(workDataOf(MISSING_ONLY to true)).build()
    )
}

class Blocklists(
    private val directory: File,
    private val sources: SourceDao,
    private val open: (URL) -> URLConnection,
    private val downloadNew: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val loading = Mutex()
    private val updating = Mutex()
    private val loaded = MutableStateFlow<Blocklist?>(null)

    val current: Flow<Blocklist> = loaded.onStart { if (loaded.value == null) reload() }.filterNotNull()

    suspend fun add(url: String, name: String) {
        val existing = sources.get(url)
        if (existing != null) return sources.save(existing.copy(name = name.ifEmpty { existing.name }))
        sources.save(ListSource(url = url, name = name, createdAt = clock()))
        downloadNew()
    }

    suspend fun remove(url: String) {
        sources.delete(url)
        withContext(Dispatchers.IO) { file(url).delete() }
        reload()
    }

    suspend fun missing(): Boolean = withContext(Dispatchers.IO) {
        sources.all().any { it.domains == null || !file(it.url).exists() }
    }

    suspend fun reload() = loading.withLock {
        loaded.value = withContext(Dispatchers.IO) {
            val lists = sources.all().map { file(it.url) }.filter(File::exists).map(::read)
            if (lists.size == 1) return@withContext Blocklist(lists.single())
            val merged = LongArray(lists.sumOf(LongArray::size))
            var offset = 0
            for (list in lists) {
                list.copyInto(merged, offset)
                offset += list.size
            }
            Blocklist(unique(merged))
        }
    }

    suspend fun update(missingOnly: Boolean): Boolean = updating.withLock {
        var complete = true
        var changed = false
        for (source in sources.all()) {
            if (missingOnly && source.domains != null && file(source.url).exists()) continue
            val downloaded = withContext(Dispatchers.IO) {
                try {
                    download(source)
                } catch (_: IOException) {
                    null
                }
            }
            val stored = sources.get(source.url)
            if (stored == null) {
                withContext(Dispatchers.IO) { file(source.url).delete() }
                continue
            }
            if (downloaded == null) {
                complete = false
                sources.save(stored.copy(failedAt = clock()))
                continue
            }
            changed = changed || downloaded !== source
            sources.save(
                stored.copy(
                    name = stored.name.ifEmpty { downloaded.name },
                    domains = downloaded.domains,
                    version = downloaded.version,
                    etag = downloaded.etag,
                    lastModified = downloaded.lastModified,
                    updatedAt = clock(),
                    failedAt = null
                )
            )
        }
        if (changed) reload()
        complete
    }

    private fun download(source: ListSource): ListSource {
        val connection = open(URL(source.url)) as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        if (source.domains != null && file(source.url).exists()) {
            source.etag?.let { connection.setRequestProperty("If-None-Match", it) }
            source.lastModified?.let { connection.setRequestProperty("If-Modified-Since", it) }
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) return source
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val parsed = connection.inputStream.use { parseList(it.reader()) }
            write(source.url, parsed.hashes)
            val etag = connection.getHeaderField("ETag")
            val lastModified = connection.getHeaderField("Last-Modified")
            return source.copy(
                name = parsed.title,
                domains = parsed.hashes.size,
                version = parsed.version ?: etag ?: lastModified,
                etag = etag,
                lastModified = lastModified
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun file(url: String) = File(directory, "%016x.bin".format(hostHash(url)))

    private fun write(url: String, hashes: LongArray) {
        directory.mkdirs()
        val target = file(url)
        val partial = File(directory, target.name + ".partial")
        val bytes = ByteBuffer.allocate(hashes.size * Long.SIZE_BYTES)
        bytes.asLongBuffer().put(hashes)
        partial.writeBytes(bytes.array())
        if (!partial.renameTo(target)) throw IOException("Couldn't save ${target.name}")
    }

    private fun read(file: File): LongArray {
        val bytes = file.readBytes()
        return LongArray(bytes.size / Long.SIZE_BYTES).also { ByteBuffer.wrap(bytes).asLongBuffer().get(it) }
    }
}

class RefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        container.database.queries().deleteBefore(System.currentTimeMillis() - LOG_RETENTION_MILLIS)
        return if (container.blocklists.update(inputData.getBoolean(MISSING_ONLY, false))) Result.success() else Result.retry()
    }
}
