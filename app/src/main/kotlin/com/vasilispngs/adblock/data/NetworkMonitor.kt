package com.vasilispngs.adblock.data

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(private val connectivity: ConnectivityManager) {
    val privateDnsServer: Flow<String?> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                trySend(linkProperties.privateDnsServerName)
            }

            override fun onLost(network: Network) {
                trySend(null)
            }
        }
        trySend(connectivity.getLinkProperties(connectivity.activeNetwork)?.privateDnsServerName)
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
