package com.vasilispngs.adblock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal const val DEFAULT_RESOLVER = "https://cloudflare-dns.com/dns-query"

private val Context.protectionPreferences: DataStore<Preferences> by preferencesDataStore(name = "protection_preferences")

class ProtectionPreferences(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.protectionPreferences)

    val enabled: Flow<Boolean> = store.data.map { it[ENABLED] ?: false }.distinctUntilChanged()

    val logEnabled: Flow<Boolean> = store.data.map { it[LOG_ENABLED] ?: true }.distinctUntilChanged()

    val resolver: Flow<String> = store.data.map { it[RESOLVER] ?: DEFAULT_RESOLVER }.distinctUntilChanged()

    suspend fun setEnabled(value: Boolean) {
        store.edit { it[ENABLED] = value }
    }

    suspend fun setLogEnabled(value: Boolean) {
        store.edit { it[LOG_ENABLED] = value }
    }

    suspend fun setResolver(value: String) {
        store.edit { it[RESOLVER] = value }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val LOG_ENABLED = booleanPreferencesKey("log_enabled")
        val RESOLVER = stringPreferencesKey("resolver")
    }
}
