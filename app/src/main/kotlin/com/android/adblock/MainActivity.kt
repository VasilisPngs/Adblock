package com.android.adblock

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.adblock.ui.DnsScreen
import com.android.adblock.ui.DnsViewModel
import com.android.adblock.ui.theme.AdblockTheme
import com.android.adblock.vpn.DohClient

class MainActivity : ComponentActivity() {
    private val viewModel: DnsViewModel by viewModels {
        viewModelFactory {
            initializer { DnsViewModel(application, DohClient.shared(application)::resolve) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        setContent {
            AdblockTheme {
                DnsScreen(viewModel = viewModel)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val light = resources.getBoolean(R.bool.light_system_bars)
        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        window.setBackgroundDrawableResource(R.color.window_background)
    }
}
