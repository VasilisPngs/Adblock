package com.android.adblock.ui

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.adblock.data.DnsPreferences
import com.android.adblock.vpn.DnsVpnService
import com.android.adblock.vpn.DohResult
import com.android.adblock.vpn.TunnelState
import com.android.adblock.vpn.addressQuery
import com.android.adblock.vpn.dohUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI

private const val STOP_TIMEOUT_MILLIS = 5_000L
private val Running = setOf(TunnelState.On, TunnelState.AlwaysOn)

data class DnsUiState(
    val url: String = "",
    val tunnel: TunnelState = TunnelState.Off,
    val privateDnsServer: String? = null,
    val vpnSettings: Boolean = false
)

sealed interface DnsEvent {
    data object Connect : DnsEvent
    data class Failed(val result: DohResult) : DnsEvent
}

class DnsViewModel(
    application: Application,
    private val resolve: suspend (String, ByteArray) -> DohResult
) : AndroidViewModel(application) {
    private val preferences = DnsPreferences(application)
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val url = MutableStateFlow(preferences.url)
    private val privateDnsServer = MutableStateFlow<String?>(null)
    private val vpnSettings = Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(application.packageManager) != null
    private val _events = Channel<DnsEvent>(Channel.BUFFERED)
    private var probe: Job? = null
    private var urlCheck: Job? = null
    val events: Flow<DnsEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<DnsUiState> =
        combine(url, DnsVpnService.tunnelState, privateDnsServer) { url, tunnel, privateDns ->
            DnsUiState(url, tunnel, privateDns, vpnSettings)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            DnsUiState(url.value, DnsVpnService.tunnelState.value, vpnSettings = vpnSettings)
        )

    fun setUrl(value: String) {
        urlCheck?.cancel()
        if (DnsVpnService.tunnelState.value !in Running) return save(value)
        urlCheck = viewModelScope.launch {
            val result = ask(value)
            if (result is DohResult.Answer) {
                save(value)
                updateTunnel()
            } else {
                _events.send(DnsEvent.Failed(result))
            }
        }
    }

    fun turnOn() {
        val server = dohUrl(url.value) ?: return
        DnsVpnService.state.value = TunnelState.Connecting
        probe = viewModelScope.launch {
            val result = ask(server)
            if (result is DohResult.Answer) {
                _events.send(DnsEvent.Connect)
            } else {
                DnsVpnService.state.value = TunnelState.Off
                _events.send(DnsEvent.Failed(result))
            }
        }
    }

    fun connect() = DnsVpnService.start(getApplication())

    fun cancel() {
        DnsVpnService.state.value = TunnelState.Off
    }

    fun turnOff() {
        probe?.cancel()
        DnsVpnService.state.compareAndSet(TunnelState.Connecting, TunnelState.Off)
        DnsVpnService.stop(getApplication())
    }

    fun refresh() {
        privateDnsServer.value = connectivity.getLinkProperties(connectivity.activeNetwork)?.privateDnsServerName
        updateTunnel()
    }

    override fun onCleared() {
        DnsVpnService.state.compareAndSet(TunnelState.Connecting, TunnelState.Off)
    }

    private suspend fun ask(server: String): DohResult = resolve(server, addressQuery(URI(server).host))

    private fun save(value: String) {
        preferences.url = value
        url.value = value
    }

    private fun updateTunnel() {
        if (DnsVpnService.tunnelState.value in Running) DnsVpnService.start(getApplication())
    }
}
