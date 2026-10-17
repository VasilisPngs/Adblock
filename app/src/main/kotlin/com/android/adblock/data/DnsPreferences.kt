package com.android.adblock.data

import android.content.Context
import androidx.core.content.edit

class DnsPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("dns_preferences", Context.MODE_PRIVATE)

    var url: String
        get() = preferences.getString(KEY_URL, null).orEmpty()
        set(value) = preferences.edit { putString(KEY_URL, value) }

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit { putBoolean(KEY_ENABLED, value) }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_ENABLED = "enabled"
    }
}
