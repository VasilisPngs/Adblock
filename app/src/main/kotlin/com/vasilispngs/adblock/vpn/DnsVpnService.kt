package com.vasilispngs.adblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.net.toUri
import com.vasilispngs.adblock.MainActivity
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

class DnsVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { appContainer }
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
        if (connecting?.isActive == true) {
            if (tunnel != null) publish()
        } else {
            container.tunnel.update(TunnelState.Connecting)
            connecting = scope.launch {
                launch {
                    container.tunnel.serverFailing.collect {
                        if (tunnel != null) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(server))
                    }
                }
                container.preferences.url.map(::dohUrl).collect { connect(it) }
            }
        }
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
            container.tunnel.serverChanged()
            startForeground(NOTIFICATION_ID, notification(url), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        }
        if (tunnel == null) {
            tunnel = establish() ?: return stop()
            container.preferences.setEnabled(true)
        }
        publish()
    }

    private fun publish() {
        container.tunnel.update(
            when {
                isLockdownEnabled -> TunnelState.Lockdown
                isAlwaysOn -> TunnelState.AlwaysOn
                else -> TunnelState.On
            }
        )
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
        val doh = container.doh
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
            val url = server
            doh.resolve(url, query.message) { result ->
                val message = (result as? DohResult.Answer)?.message ?: serverFailure(query.message)
                try {
                    output.write(dnsResponsePacket(query, message))
                } catch (_: IOException) {
                }
                if (url == server) container.tunnel.report(result)
            }
        }
    }

    private fun stop() {
        connecting?.cancel()
        connecting = null
        scope.launch { withContext(NonCancellable) { container.preferences.setEnabled(false) } }
        close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun close() {
        tunnel?.close()
        tunnel = null
        container.tunnel.update(TunnelState.Off)
    }

    private fun notification(url: String?): Notification {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.dns_over_https), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val host = url?.toUri()?.host
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.dns_over_https_on))
            .setContentText(if (host != null && container.tunnel.serverFailing.value) getString(R.string.server_not_answering_host, host) else host)
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        internal const val ACTION_STOP = "com.vasilispngs.adblock.STOP"
        private const val CHANNEL_ID = "dns"
        private const val NOTIFICATION_ID = 1
        private const val TUNNEL_ADDRESS = "198.18.0.1"
        private const val DNS_ADDRESS = "198.18.0.53"
        private const val MAX_PACKET = 0xFFFF
    }
}
