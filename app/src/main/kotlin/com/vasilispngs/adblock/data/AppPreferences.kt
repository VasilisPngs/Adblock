package com.vasilispngs.adblock.data

import android.app.UiModeManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferences: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(
    private val context: Context,
    private val store: DataStore<Preferences> = context.appPreferences
) {
    val themeMode: Flow<ThemeMode> = store.data.map { it.themeMode() }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
        applyNightMode(mode)
    }

    suspend fun restoreThemeMode(): ThemeMode = themeMode.first().also(::applyNightMode)

    private fun Preferences.themeMode(): ThemeMode = ThemeMode.entries.firstOrNull { it.name == this[THEME_MODE] } ?: ThemeMode.System

    private fun applyNightMode(mode: ThemeMode) {
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.nightMode)
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
