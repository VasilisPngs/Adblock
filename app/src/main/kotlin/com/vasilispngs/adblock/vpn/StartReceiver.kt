package com.vasilispngs.adblock.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.vasilispngs.adblock.appContainer
import com.vasilispngs.adblock.data.consumeSelfUpdate
import com.vasilispngs.adblock.data.notifyUpdated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = context.appContainer
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED && context.consumeSelfUpdate()) {
                    context.notifyUpdated()
                }
                if (container.preferences.enabled.first() && VpnService.prepare(context) == null) container.tunnel.start()
            } finally {
                result.finish()
            }
        }
    }
}
