package com.vasilispngs.adblock

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.http.HttpEngine
import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.work.Configuration
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.AdblockDatabase
import com.vasilispngs.adblock.data.AppPreferences
import com.vasilispngs.adblock.data.Blocklists
import com.vasilispngs.adblock.data.NetworkMonitor
import com.vasilispngs.adblock.data.ProtectionPreferences
import com.vasilispngs.adblock.data.QueryLog
import com.vasilispngs.adblock.data.SelfUpdater
import com.vasilispngs.adblock.data.downloadNewLists
import com.vasilispngs.adblock.dns.DohClient
import com.vasilispngs.adblock.dns.EngineState
import com.vasilispngs.adblock.dns.Rules
import com.vasilispngs.adblock.vpn.DnsTunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File

class AdblockApplication : Application(), Configuration.Provider {
    val container by lazy { AppContainer(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate() {
        super.onCreate()
        ComposeMaterial3Flags.isSnackbarStylingFixEnabled = true
    }
}

class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http by lazy { HttpEngine.Builder(context).build() }

    val database by lazy { AdblockDatabase.create(context) }
    val preferences = ProtectionPreferences(context)
    val appPreferences = AppPreferences(context)
    val doh by lazy { DohClient(http) }
    val selfUpdater = SelfUpdater(context, scope) { url -> http.openConnection(url) }
    val tunnel = DnsTunnel(context)
    val network = NetworkMonitor(context.getSystemService(ConnectivityManager::class.java))
    val blocklists by lazy {
        Blocklists(File(context.filesDir, "lists"), database.sources(), { url -> http.openConnection(url) }, { downloadNewLists(context) })
    }
    val queryLog by lazy { QueryLog(database.queries(), scope) }

    val engineState: Flow<EngineState> by lazy {
        combine(
            database.rules().observe().map { rules ->
                Rules(
                    allow = rules.filter { it.action == Action.Allow }.mapTo(HashSet()) { it.host },
                    block = rules.filter { it.action == Action.Block }.mapTo(HashSet()) { it.host }
                )
            },
            blocklists.current,
            preferences.resolver,
            preferences.logEnabled,
            ::EngineState
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AdblockApplication).container
