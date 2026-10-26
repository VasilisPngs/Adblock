package com.vasilispngs.adblock.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vasilispngs.adblock.AdblockApplication
import com.vasilispngs.adblock.data.AppPreferences
import com.vasilispngs.adblock.data.Appearance
import com.vasilispngs.adblock.data.ProtectionPreferences
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.SelfUpdater
import com.vasilispngs.adblock.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

class SettingsViewModel(
    private val appPreferences: AppPreferences,
    private val preferences: ProtectionPreferences,
    private val selfUpdater: SelfUpdater
) : ViewModel() {
    val appearance: StateFlow<Appearance?> = appPreferences.appearance
        .onStart { appPreferences.restoreThemeMode() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val logEnabled: StateFlow<Boolean> = preferences.logEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    val selfUpdate: StateFlow<SelfUpdate> = selfUpdater.status

    val updateConfirmation: StateFlow<Intent?> = selfUpdater.confirmation

    init {
        selfUpdater.check()
    }

    fun setThemeMode(mode: ThemeMode) {
        if (appearance.value?.themeMode == mode) return
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        if (appearance.value?.dynamicColor == enabled) return
        viewModelScope.launch { appPreferences.setDynamicColor(enabled) }
    }

    fun setLogEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setLogEnabled(enabled) }
    }

    fun updateApp() = selfUpdater.update()

    fun updateConfirmationShown(intent: Intent) = selfUpdater.confirmationShown(intent)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AdblockApplication).container
                SettingsViewModel(container.appPreferences, container.preferences, container.selfUpdater)
            }
        }
    }
}
