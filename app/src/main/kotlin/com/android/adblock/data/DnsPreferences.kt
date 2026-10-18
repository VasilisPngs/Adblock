package com.android.adblock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val NAME = "dns_preferences"

private val Context.dnsPreferences: DataStore<Preferences> by preferencesDataStore(
    name = NAME,
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, NAME)) }
)

class DnsPreferences(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dnsPreferences)

    val url: Flow<String> = store.data.map { it[URL].orEmpty() }.distinctUntilChanged()

    val enabled: Flow<Boolean> = store.data.map { it[ENABLED] ?: false }.distinctUntilChanged()

    suspend fun setUrl(value: String) {
        store.edit { it[URL] = value }
    }

    suspend fun setEnabled(value: Boolean) {
        store.edit { it[ENABLED] = value }
    }

    private companion object {
        val URL = stringPreferencesKey("url")
        val ENABLED = booleanPreferencesKey("enabled")
    }
}
