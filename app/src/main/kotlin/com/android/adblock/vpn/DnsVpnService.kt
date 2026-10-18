package com.android.adblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.net.toUri
import com.android.adblock.MainActivity
import com.android.adblock.R
import com.android.adblock.data.DnsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

enum class TunnelState { Off, Connecting, On, AlwaysOn }

class DnsVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences by lazy { DnsPreferences(this) }
    private var tunnel: ParcelFileDescriptor? = null
    private var connecting: Job? = null

    @Volatile
    private var server = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(server.ifEmpty { null }), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        connecting?.cancel()
        connecting = scope.launch { connect(dohUrl(preferences.url())) }
        return START_STICKY
    }

    override fun onRevoke() = mainExecutor.execute(::stop)

    override fun onDestroy() {
        scope.cancel()
        close()
        super.onDestroy()
    }

    private suspend fun connect(url: String?) {
        if (url == null) return stop()
        if (url != server) {
            server = url
            startForeground(NOTIFICATION_ID, notification(url), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        }
        if (tunnel == null) {
            tunnel = establish() ?: return stop()
            preferences.setEnabled(true)
        }
        state.value = if (isAlwaysOn) TunnelState.AlwaysOn else TunnelState.On
    }

    private fun establish(): ParcelFileDescriptor? {
        val tunnel = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(TUNNEL_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, 32)
            .allowFamily(OsConstants.AF_INET6)
            .addDisallowedApplication(packageName)
            .setMetered(false)
            .setBlocking(true)
            .setConfigureIntent(openApp())
            .establish() ?: return null
        val doh = DohClient.shared(this)
        thread(name = "dns-tunnel") { forward(tunnel, doh) }
        return tunnel
    }

    private fun forward(tunnel: ParcelFileDescriptor, doh: DohClient) {
        val input = FileInputStream(tunnel.fileDescriptor)
        val output = FileOutputStream(tunnel.fileDescriptor)
        val packet = ByteArray(MAX_PACKET)
        while (true) {
            val length = try {
                input.read(packet)
            } catch (_: IOException) {
                return
            }
            if (length < 0) return
            val query = parseDnsQuery(packet, length) ?: continue
            doh.resolve(server, query.message) { result ->
                val message = (result as? DohResult.Answer)?.message ?: serverFailure(query.message)
                try {
                    output.write(dnsResponsePacket(query, message))
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun stop() {
        connecting?.cancel()
        scope.launch { withContext(NonCancellable) { preferences.setEnabled(false) } }
        close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun close() {
        tunnel?.close()
        tunnel = null
        state.value = TunnelState.Off
    }

    private fun notification(url: String?): Notification {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.dns_over_https), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.dns_over_https_on))
            .setContentText(url?.toUri()?.host)
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        private const val ACTION_STOP = "com.android.adblock.STOP"
        private const val CHANNEL_ID = "dns"
        private const val NOTIFICATION_ID = 1
        private const val TUNNEL_ADDRESS = "198.18.0.1"
        private const val DNS_ADDRESS = "198.18.0.53"
        private const val MAX_PACKET = 0xFFFF

        internal val state = MutableStateFlow(TunnelState.Off)
        val tunnelState: StateFlow<TunnelState> = state.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DnsVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DnsVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
