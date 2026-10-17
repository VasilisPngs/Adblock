package com.android.adblock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DnsPreferencesTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `starts with no server and turned off`() {
        val preferences = DnsPreferences(context)

        assertEquals("", preferences.url)
        assertFalse(preferences.enabled)
    }

    @Test
    fun `keeps the server and whether it is on`() {
        DnsPreferences(context).apply {
            url = "https://dns.nextdns.io/abc123"
            enabled = true
        }

        val preferences = DnsPreferences(context)
        assertEquals("https://dns.nextdns.io/abc123", preferences.url)
        assertTrue(preferences.enabled)
    }
}
