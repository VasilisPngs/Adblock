package com.android.adblock

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.adblock.data.DnsPreferences
import com.android.adblock.data.NetworkMonitor
import com.android.adblock.vpn.DnsTunnel
import com.android.adblock.vpn.DohClient

class AdblockApplication : Application() {
    val container by lazy { AppContainer(this) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate() {
        super.onCreate()
        ComposeMaterial3Flags.isSnackbarStylingFixEnabled = true
    }
}

class AppContainer(context: Context) {
    val preferences = DnsPreferences(context)
    val doh = DohClient(context)
    val tunnel = DnsTunnel(context)
    val network = NetworkMonitor(context.getSystemService(ConnectivityManager::class.java))
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AdblockApplication).container
