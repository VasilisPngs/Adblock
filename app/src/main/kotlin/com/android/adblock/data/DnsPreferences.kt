package com.android.adblock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private const val NAME = "dns_preferences"

private val Context.dnsPreferences: DataStore<Preferences> by preferencesDataStore(
    name = NAME,
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, NAME)) }
)

class DnsPreferences(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dnsPreferences)

    suspend fun url(): String = store.data.first()[URL].orEmpty()

    suspend fun setUrl(value: String) {
        store.edit { it[URL] = value }
    }

    suspend fun enabled(): Boolean = store.data.first()[ENABLED] ?: false

    suspend fun setEnabled(value: Boolean) {
        store.edit { it[ENABLED] = value }
    }

    private companion object {
        val URL = stringPreferencesKey("url")
        val ENABLED = booleanPreferencesKey("enabled")
    }
}
