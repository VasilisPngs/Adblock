package com.android.adblock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.adblock.AdblockApplication
import com.android.adblock.data.AppPreferences
import com.android.adblock.data.Appearance
import com.android.adblock.data.DnsPreferences
import com.android.adblock.data.ThemeMode
import com.android.adblock.vpn.DnsTunnel
import com.android.adblock.vpn.DohResult
import com.android.adblock.vpn.TunnelState
import com.android.adblock.vpn.addressQuery
import com.android.adblock.vpn.dohUrl
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
private val Running = setOf(TunnelState.On, TunnelState.AlwaysOn)

data class DnsUiState(
    val url: String = "",
    val tunnel: TunnelState = TunnelState.Off,
    val privateDnsServer: String? = null,
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
    private val resolve: suspend (String, ByteArray) -> DohResult
) : ViewModel() {
    private val request = MutableStateFlow(Request())
    private var probe: Job? = null
    private var urlCheck: Job? = null

    val uiState: StateFlow<DnsUiState> =
        combine(preferences.url, tunnel.state, privateDnsServer, request) { url, state, privateDns, request ->
            DnsUiState(
                url = url,
                tunnel = if (request.starting && state == TunnelState.Off) TunnelState.Connecting else state,
                privateDnsServer = privateDns,
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

    val appearance: StateFlow<Appearance?> = appPreferences.appearance
        .onStart { appPreferences.restoreThemeMode() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

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
        if (appearance.value?.themeMode == mode) return
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        if (appearance.value?.dynamicColor == enabled) return
        viewModelScope.launch { appPreferences.setDynamicColor(enabled) }
    }

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
                    container.doh::resolve
                )
            }
        }
    }
}
