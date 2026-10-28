package com.vasilispngs.adblock.vpn

import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TunnelState { Off, Connecting, On, AlwaysOn, Lockdown }

class DnsTunnel(private val context: Context) {
    private val _state = MutableStateFlow(TunnelState.Off)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _serverFailing = MutableStateFlow(false)
    val serverFailing: StateFlow<Boolean> = _serverFailing.asStateFlow()

    val vpnSettings = Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(context.packageManager) != null

    fun start() {
        context.startForegroundService(Intent(context, DnsVpnService::class.java))
    }

    fun stop() {
        context.startService(Intent(context, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP))
    }

    internal fun update(state: TunnelState) {
        _state.value = state
        if (state == TunnelState.Connecting) _serverFailing.value = false
    }

    internal fun serverChanged() {
        _serverFailing.value = false
    }

    internal fun report(result: DohResult) {
        _serverFailing.value = when (result) {
            is DohResult.Answer -> false
            is DohResult.Rejected, DohResult.Failed -> true
            DohResult.Unreachable -> return
        }
    }
}
