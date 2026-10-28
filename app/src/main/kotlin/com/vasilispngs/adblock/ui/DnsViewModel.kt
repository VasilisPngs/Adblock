package com.vasilispngs.adblock.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vasilispngs.adblock.AdblockApplication
import com.vasilispngs.adblock.data.AppPreferences
import com.vasilispngs.adblock.data.DnsPreferences
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.SelfUpdater
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.vpn.DnsTunnel
import com.vasilispngs.adblock.vpn.DohResult
import com.vasilispngs.adblock.vpn.TunnelState
import com.vasilispngs.adblock.vpn.addressQuery
import com.vasilispngs.adblock.vpn.dohUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI

private const val STOP_TIMEOUT_MILLIS = 5_000L
private val Running = setOf(TunnelState.On, TunnelState.AlwaysOn, TunnelState.Lockdown)

data class DnsUiState(
    val url: String = "",
    val tunnel: TunnelState = TunnelState.Off,
    val privateDnsServer: String? = null,
    val serverFailing: Boolean = false,
    val vpnSettings: Boolean = false,
    val loaded: Boolean = false,
    val readyToConnect: Boolean = false,
    val failure: DohResult? = null
)

private data class Request(
    val starting: Boolean = false,
    val readyToConnect: Boolean = false,
    val failure: DohResult? = null
)

class DnsViewModel(
    private val preferences: DnsPreferences,
    private val appPreferences: AppPreferences,
    private val tunnel: DnsTunnel,
    privateDnsServer: Flow<String?>,
    private val selfUpdater: SelfUpdater,
    private val resolve: suspend (String, ByteArray) -> DohResult
) : ViewModel() {
    private val request = MutableStateFlow(Request())
    private var probe: Job? = null
    private var urlCheck: Job? = null

    val uiState: StateFlow<DnsUiState> =
        combine(preferences.url, tunnel.state, privateDnsServer, request, tunnel.serverFailing) { url, state, privateDns, request, failing ->
            DnsUiState(
                url = url,
                tunnel = if (request.starting && state == TunnelState.Off) TunnelState.Connecting else state,
                privateDnsServer = privateDns,
                serverFailing = failing && state in Running,
                vpnSettings = tunnel.vpnSettings,
                loaded = true,
                readyToConnect = request.readyToConnect,
                failure = request.failure
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            DnsUiState(tunnel = tunnel.state.value, vpnSettings = tunnel.vpnSettings)
        )

    val selfUpdate: StateFlow<SelfUpdate> = selfUpdater.status

    val updateConfirmation: StateFlow<Intent?> = selfUpdater.confirmation

    val themeMode: StateFlow<ThemeMode?> = appPreferences.themeMode
        .onStart { appPreferences.restoreThemeMode() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        selfUpdater.check()
    }

    fun setUrl(value: String) {
        urlCheck?.cancel()
        urlCheck = viewModelScope.launch {
            if (tunnel.state.value !in Running) return@launch preferences.setUrl(value)
            val result = ask(value)
            if (result is DohResult.Answer) {
                preferences.setUrl(value)
            } else {
                request.update { it.copy(failure = result) }
            }
        }
    }

    fun turnOn() {
        val server = dohUrl(uiState.value.url) ?: return
        request.update { it.copy(starting = true) }
        probe = viewModelScope.launch {
            val result = ask(server)
            request.update {
                if (result is DohResult.Answer) it.copy(readyToConnect = true) else it.copy(starting = false, failure = result)
            }
        }
    }

    fun connectRequestHandled() {
        request.update { it.copy(readyToConnect = false) }
    }

    fun connect() {
        tunnel.start()
        viewModelScope.launch {
            tunnel.state.first { it != TunnelState.Off }
            request.update { it.copy(starting = false) }
        }
    }

    fun cancel() {
        request.update { it.copy(starting = false) }
    }

    fun turnOff() {
        probe?.cancel()
        request.update { it.copy(starting = false, readyToConnect = false) }
        tunnel.stop()
    }

    fun refresh() {
        if (tunnel.state.value in Running) tunnel.start()
    }

    fun failureShown() {
        request.update { it.copy(failure = null) }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (themeMode.value == mode) return
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    fun updateApp() = selfUpdater.update()

    fun updateConfirmationShown(intent: Intent) = selfUpdater.confirmationShown(intent)

    private suspend fun ask(server: String): DohResult = resolve(server, addressQuery(URI(server).host))

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AdblockApplication).container
                DnsViewModel(
                    container.preferences,
                    container.appPreferences,
                    container.tunnel,
                    container.network.privateDnsServer,
                    container.selfUpdater,
                    container.doh::resolve
                )
            }
        }
    }
}
