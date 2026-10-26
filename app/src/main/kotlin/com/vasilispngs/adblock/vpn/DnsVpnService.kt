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
import com.vasilispngs.adblock.MainActivity
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.appContainer
import com.vasilispngs.adblock.data.downloadNewLists
import com.vasilispngs.adblock.data.scheduleDailyLists
import com.vasilispngs.adblock.dns.DnsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        if (connecting?.isActive == true) {
            if (tunnel != null) publish()
        } else {
            container.tunnel.update(TunnelState.Connecting)
            connecting = scope.launch { connect() }
        }
        return START_STICKY
    }

    override fun onRevoke() = mainExecutor.execute(::stop)

    override fun onDestroy() {
        scope.cancel()
        close()
        super.onDestroy()
    }

    private suspend fun connect() {
        val engine = withContext(Dispatchers.IO) { DnsEngine(container.doh, container.queryLog::add) }
        engine.state = container.engineState.first()
        tunnel = establish(engine) ?: return stop()
        container.preferences.setEnabled(true)
        publish()
        scheduleDailyLists(this)
        if (container.blocklists.missing()) downloadNewLists(this)
        container.engineState.collect { engine.state = it }
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

    private fun establish(engine: DnsEngine): ParcelFileDescriptor? {
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
        thread(name = "dns-tunnel") { forward(tunnel, engine) }
        return tunnel
    }

    private fun forward(tunnel: ParcelFileDescriptor, engine: DnsEngine) {
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
            engine.handle(query.message) { message ->
                val response = dnsResponsePacket(query, message)
                synchronized(output) {
                    try {
                        output.write(response)
                    } catch (_: IOException) {
                    }
                }
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

    private fun notification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.protection), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.protection_on))
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
