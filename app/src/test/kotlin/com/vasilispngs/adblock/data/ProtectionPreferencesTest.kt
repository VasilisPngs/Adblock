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

class ProtectionPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `starts off, logging, and resolving through Cloudflare`() = runTest {
        val preferences = preferences(backgroundScope)

        assertFalse(preferences.enabled.first())
        assertTrue(preferences.logEnabled.first())
        assertEquals("https://cloudflare-dns.com/dns-query", preferences.resolver.first())
    }

    @Test
    fun `keeps whether it is on, the log setting and the resolver`() = runTest {
        val writer = Job()
        preferences(CoroutineScope(coroutineContext + writer)).apply {
            setEnabled(true)
            setLogEnabled(false)
            setResolver("https://dns.google/dns-query")
        }
        writer.cancelAndJoin()

        val preferences = preferences(backgroundScope)
        assertTrue(preferences.enabled.first())
        assertFalse(preferences.logEnabled.first())
        assertEquals("https://dns.google/dns-query", preferences.resolver.first())
    }

    private fun preferences(scope: CoroutineScope) =
        ProtectionPreferences(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "protection.preferences_pb") })
}
