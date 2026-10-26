package com.vasilispngs.adblock.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.AdblockDatabase
import com.vasilispngs.adblock.data.Blocklists
import com.vasilispngs.adblock.data.DEFAULT_LIST_URL
import com.vasilispngs.adblock.data.ProtectionPreferences
import com.vasilispngs.adblock.data.Rule
import com.vasilispngs.adblock.dns.Resolver
import com.vasilispngs.adblock.dns.answer
import com.vasilispngs.adblock.dns.parse
import com.vasilispngs.adblock.dns.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProtectionViewModelTest {
    @get:JUnitRule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val database = AdblockDatabase.create(context)
    private val probed = mutableListOf<String>()
    private var answers = true
    private val downloads = MutableStateFlow(0)
    private val resolver = Resolver { url, message, onResult ->
        probed += url
        onResult(if (answers) answer(message, answers = emptyList()) else null)
    }
    private val blocklists = Blocklists(
        File(context.filesDir, "lists"),
        database.sources(),
        { throw IOException("Offline") },
        { downloads.update { it + 1 } }
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `shows the lists, the rules and the resolver`() = runTest {
        val state = viewModel().uiState.first { it.loaded }

        assertEquals(listOf(DEFAULT_LIST_URL), state.sources.map { it.url })
        assertTrue(state.rules.isEmpty())
        assertEquals("https://cloudflare-dns.com/dns-query", state.resolver)
    }

    @Test
    fun `adds a list, downloads it and removes it`() = runTest {
        val viewModel = viewModel()

        viewModel.addSource(OTHER, " Other ")
        val added = viewModel.uiState.await { it.sources.size == 2 }
        assertEquals("Other", added.sources.last().name)
        assertEquals(1, downloads.await { it > 0 })

        viewModel.removeSource(OTHER)
        assertEquals(listOf(DEFAULT_LIST_URL), viewModel.uiState.await { it.sources.size == 1 }.sources.map { it.url })
    }

    @Test
    fun `adds and removes custom rules, newest first`() = runTest {
        var now = 1L
        val viewModel = viewModel(clock = { now++ })

        viewModel.addRule("ads.example", Action.Block)
        viewModel.addRule("safe.example", Action.Allow)
        assertEquals(
            listOf(Rule("safe.example", Action.Allow, 2), Rule("ads.example", Action.Block, 1)),
            viewModel.uiState.await { it.rules.size == 2 }.rules
        )

        viewModel.removeRule("ads.example")
        assertEquals(listOf("safe.example"), viewModel.uiState.await { it.rules.size == 1 }.rules.map { it.host })
    }

    @Test
    fun `saves a resolver only after it answers a test query`() = runTest {
        val preferences = preferences()
        val viewModel = viewModel(preferences)

        viewModel.setResolver(GOOGLE)

        assertEquals(ProtectionMessage.Saved, viewModel.uiState.first { it.message != null }.message)
        assertEquals(GOOGLE, preferences.resolver.first())
        assertEquals(listOf(GOOGLE), probed)
        viewModel.messageShown()
        assertNull(viewModel.uiState.first { it.message == null }.message)
    }

    @Test
    fun `keeps the resolver and says why when the new one does not answer`() = runTest {
        answers = false
        val preferences = preferences()
        val viewModel = viewModel(preferences)

        viewModel.setResolver(GOOGLE)

        assertEquals(ProtectionMessage.ResolverUnreachable, viewModel.uiState.first { it.message != null }.message)
        assertEquals("https://cloudflare-dns.com/dns-query", preferences.resolver.first())
    }

    @Test
    fun `does not test the resolver it already uses`() = runTest {
        viewModel().setResolver("https://cloudflare-dns.com/dns-query")
        assertTrue(probed.isEmpty())
    }

    @Test
    fun `accepts only domains as rules`() {
        assertEquals("ads.example", ruleHost("  .Ads.Example. "))
        assertNull(ruleHost("localhost"))
        assertNull(ruleHost("https://ads.example"))
        assertNull(ruleHost("*.ads.example"))
    }

    @Test
    fun `tests the resolver with a recursive question for example_com`() = runTest {
        var asked: ByteArray? = null
        val viewModel = ProtectionViewModel(blocklists, database.sources(), database.rules(), preferences(), { _, message, onResult ->
            asked = message
            onResult(null)
        })
        viewModel.setResolver(GOOGLE)
        viewModel.uiState.first { it.message != null }
        assertEquals("example.com", parse(asked!!).question.name)
        assertEquals(query("example.com", id = 0).toList(), asked!!.toList())
    }

    private fun TestScope.preferences() =
        ProtectionPreferences(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "protection.preferences_pb") })

    private suspend fun TestScope.viewModel(
        preferences: ProtectionPreferences = preferences(),
        clock: () -> Long = System::currentTimeMillis
    ): ProtectionViewModel {
        val viewModel = ProtectionViewModel(blocklists, database.sources(), database.rules(), preferences, resolver, clock)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.loaded }
        return viewModel
    }

    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(WAIT_MILLIS) { first(predicate) } }

    private companion object {
        const val OTHER = "https://lists.example/other.txt"
        const val GOOGLE = "https://dns.google/dns-query"
        const val WAIT_MILLIS = 5_000L
    }
}
