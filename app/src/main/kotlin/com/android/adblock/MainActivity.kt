package com.android.adblock

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.adblock.ui.DnsScreen
import com.android.adblock.ui.DnsViewModel
import com.android.adblock.ui.theme.AdblockTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DnsViewModel by viewModels { DnsViewModel.Factory }
    private var windowBackground = R.color.window_background
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            appearance?.let { current ->
                AdblockTheme(dynamicColor = current.dynamicColor) {
                    DnsScreen(viewModel = viewModel, appearance = current)
                }
                SideEffect { showWindow(current.dynamicColor) }
            }
        }
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val light = resources.getBoolean(R.bool.light_system_bars)
        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        window.setBackgroundDrawableResource(windowBackground)
    }

    private fun showWindow(dynamicColor: Boolean) {
        val background = if (dynamicColor) R.color.dynamic_window_background else R.color.window_background
        if (!ready || background != windowBackground) {
            windowBackground = background
            window.setBackgroundDrawableResource(background)
            splashScreen.setSplashScreenTheme(if (dynamicColor) R.style.Theme_Adblock_DynamicColor else Resources.ID_NULL)
        }
        ready = true
    }
}
