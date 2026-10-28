package com.vasilispngs.adblock.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import com.vasilispngs.adblock.MainActivity
import com.vasilispngs.adblock.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.security.MessageDigest

internal const val UPDATED_MARKER = "self-update"

private class Release(val url: String, val size: Long, val digest: String)

class SelfUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val open: (URL) -> URLConnection
) {
    private val state = MutableStateFlow<SelfUpdate>(SelfUpdate.Idle)
    private val pendingConfirmation = MutableStateFlow<Intent?>(null)
    private val apk = File(context.cacheDir, APK_NAME)
    private var release: Release? = null
    private var work: Job? = null

    val status: StateFlow<SelfUpdate> = state.asStateFlow()
    val confirmation: StateFlow<Intent?> = pendingConfirmation.asStateFlow()

    fun check() {
        if (work?.isActive == true) return
        work = scope.launch {
            val newer = try {
                newerRelease()
            } catch (_: IOException) {
                null
            } catch (_: JSONException) {
                null
            }
            release = newer
            state.value = if (newer == null) SelfUpdate.Idle else SelfUpdate.Available
        }
    }

    fun update() {
        val newer = release ?: return
        if (state.value != SelfUpdate.Available && state.value != SelfUpdate.Failed) return
        work = scope.launch {
            if (download(newer)) install()
        }
    }

    fun confirmationShown(intent: Intent) {
        pendingConfirmation.compareAndSet(intent, null)
    }

    private suspend fun newerRelease(): Release? = withContext(Dispatchers.IO) {
        apk.delete()
        val assets = fetch(RELEASE_URL, GITHUB_HEADERS) { input, _ -> JSONObject(input.reader().readText()).getJSONArray("assets") }
        val asset = (0 until assets.length()).map(assets::getJSONObject).firstOrNull { it.getString("name") == APK_NAME }
            ?: throw IOException("No $APK_NAME in the release")
        val installed = File(context.applicationInfo.sourceDir).inputStream().use(::sha256)
        Release(asset.getString("browser_download_url"), asset.getLong("size"), asset.getString("digest"))
            .takeIf { it.digest != installed }
    }

    private suspend fun download(release: Release): Boolean {
        state.value = SelfUpdate.Downloading(0f)
        val downloaded = try {
            withContext(Dispatchers.IO) {
                fetch(release.url) { input, length ->
                    val total = length.takeIf { it > 0 } ?: release.size
                    apk.outputStream().use { output ->
                        var copied = 0L
                        sha256(input) { buffer, read ->
                            output.write(buffer, 0, read)
                            copied += read
                            state.value = SelfUpdate.Downloading(copied.toFloat() / total)
                        }
                    }
                } == release.digest
            }
        } catch (_: IOException) {
            false
        }
        if (!downloaded) state.value = SelfUpdate.Failed
        return downloaded
    }

    private suspend fun install() {
        state.value = SelfUpdate.Installing
        val marker = File(context.noBackupFilesDir, UPDATED_MARKER)
        val installed = try {
            withContext(Dispatchers.IO) {
                marker.createNewFile()
                commit()
            }
        } catch (_: IOException) {
            false
        }
        if (!installed) {
            marker.delete()
            state.value = SelfUpdate.Failed
        }
        apk.delete()
    }

    private suspend fun commit(): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(apk.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        val action = "${context.packageName}.SELF_UPDATE_$id"
        val result = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION ->
                        pendingConfirmation.value = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    PackageInstaller.STATUS_SUCCESS -> result.complete(true)
                    else -> result.complete(false)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        return try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(APK_NAME, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(
                    PendingIntent.getBroadcast(
                        context,
                        id,
                        Intent(action).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    ).intentSender
                )
            }
            result.await()
        } catch (_: IOException) {
            installer.abandonSession(id)
            false
        } finally {
            pendingConfirmation.value = null
            context.unregisterReceiver(receiver)
        }
    }

    private fun <T> fetch(url: String, headers: Map<String, String> = emptyMap(), read: (InputStream, Long) -> T): T {
        val connection = open(URL(url)) as HttpURLConnection
        headers.forEach(connection::setRequestProperty)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { read(it, connection.contentLengthLong) }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val APK_NAME = "Adblock.apk"
        const val RELEASE_URL = "https://api.github.com/repos/VasilisPngs/Adblock/releases/tags/Adblock"
        val GITHUB_HEADERS = mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2026-03-10")
    }
}

internal fun sha256(input: InputStream, onRead: (ByteArray, Int) -> Unit = { _, _ -> }): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
        onRead(buffer, read)
    }
    return "sha256:" + digest.digest().toHexString()
}

internal fun Context.notifyUpdated() {
    val notifications = getSystemService(NotificationManager::class.java)
    notifications.createNotificationChannel(
        NotificationChannel(UPDATES_CHANNEL, getString(R.string.updates), NotificationManager.IMPORTANCE_LOW)
    )
    notifications.notify(
        UPDATED_ID,
        Notification.Builder(this, UPDATES_CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.app_updated))
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
    )
}

private const val BUFFER_SIZE = 64 * 1024
private const val UPDATES_CHANNEL = "updates"
private const val UPDATED_ID = 2
