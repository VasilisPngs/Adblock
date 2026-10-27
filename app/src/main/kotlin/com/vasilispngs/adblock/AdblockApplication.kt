package com.vasilispngs.adblock

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.http.ConnectionMigrationOptions
import android.net.http.HttpEngine
import android.net.http.QuicOptions
import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import com.vasilispngs.adblock.data.AppPreferences
import com.vasilispngs.adblock.data.DnsPreferences
import com.vasilispngs.adblock.data.NetworkMonitor
import com.vasilispngs.adblock.data.SelfUpdater
import com.vasilispngs.adblock.vpn.DnsTunnel
import com.vasilispngs.adblock.vpn.DohClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AdblockApplication : Application() {
    val container by lazy { AppContainer(this) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate() {
        super.onCreate()
        ComposeMaterial3Flags.isSnackbarStylingFixEnabled = true
    }
}

class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = HttpEngine.Builder(context)
        .setStoragePath(context.cacheDir.resolve(HTTP_STORAGE).apply { mkdirs() }.path)
        .setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_DISK_NO_HTTP, HTTP_CACHE_BYTES)
        .setQuicOptions(QuicOptions.Builder().setInMemoryServerConfigsCacheSize(QUIC_SERVER_CONFIGS).build())
        .setConnectionMigrationOptions(
            ConnectionMigrationOptions.Builder()
                .setDefaultNetworkMigration(ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED)
                .setPathDegradationMigration(ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED)
                .build()
        )
        .build()

    val preferences = DnsPreferences(context)
    val appPreferences = AppPreferences(context)
    val doh = DohClient(http)
    val selfUpdater = SelfUpdater(context, scope, http::openConnection)
    val tunnel = DnsTunnel(context)
    val network = NetworkMonitor(context.getSystemService(ConnectivityManager::class.java))
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AdblockApplication).container

private const val HTTP_STORAGE = "http"
private const val HTTP_CACHE_BYTES = 1L shl 20
private const val QUIC_SERVER_CONFIGS = 4
