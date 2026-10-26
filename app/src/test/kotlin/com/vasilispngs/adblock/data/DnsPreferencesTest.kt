package com.vasilispngs.adblock.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DnsPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `starts with no server and turned off`() = runTest {
        val preferences = preferences(backgroundScope)

        assertEquals("", preferences.url.first())
        assertFalse(preferences.enabled.first())
    }

    @Test
    fun `keeps the server and whether it is on`() = runTest {
        val writer = Job()
        preferences(CoroutineScope(coroutineContext + writer)).apply {
            setUrl("https://dns.nextdns.io/abc123")
            setEnabled(true)
        }
        writer.cancelAndJoin()

        val preferences = preferences(backgroundScope)
        assertEquals("https://dns.nextdns.io/abc123", preferences.url.first())
        assertTrue(preferences.enabled.first())
    }

    private fun preferences(scope: CoroutineScope) =
        DnsPreferences(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "dns.preferences_pb") })
}
