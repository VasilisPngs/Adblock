package com.vasilispngs.adblock

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.http.HttpEngine
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
    private val http = HttpEngine.Builder(context).build()

    val preferences = DnsPreferences(context)
    val appPreferences = AppPreferences(context)
    val doh = DohClient(http)
    val selfUpdater = SelfUpdater(context, scope, http::openConnection)
    val tunnel = DnsTunnel(context)
    val network = NetworkMonitor(context.getSystemService(ConnectivityManager::class.java))
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AdblockApplication).container
