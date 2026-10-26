package com.vasilispngs.adblock.ui

import androidx.annotation.StringRes
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.DEFAULT_LIST_NAME
import com.vasilispngs.adblock.data.DEFAULT_LIST_URL
import com.vasilispngs.adblock.data.ListSource
import com.vasilispngs.adblock.data.Rule
import com.vasilispngs.adblock.ui.theme.AdblockTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class ProtectionScreenTest {
    @get:JUnitRule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private var state by mutableStateOf(ProtectionUiState())
    private val addedSources = mutableListOf<Pair<String, String>>()
    private val removedSources = mutableListOf<String>()
    private val addedRules = mutableListOf<Pair<String, Action>>()
    private val removedRules = mutableListOf<String>()
    private val resolvers = mutableListOf<String>()

    private val now = System.currentTimeMillis()
    private val downloaded = ListSource(DEFAULT_LIST_URL, DEFAULT_LIST_NAME, 0, domains = 85515, updatedAt = now - 2 * 3_600_000L)

    @Test
    fun `shows each list with its size and when it was updated`() {
        show(
            ProtectionUiState(
                sources = listOf(
                    downloaded,
                    ListSource("https://lists.example/new.txt", "", 1),
                    ListSource("https://lists.example/broken.txt", "Broken", 2, failedAt = now)
                ),
                resolver = RESOLVER,
                loaded = true
            )
        )

        compose.onNodeWithText(DEFAULT_LIST_NAME).assertIsDisplayed()
        compose.onNode(hasText("85,515 domains · Updated 2 hours ago")).assertIsDisplayed()
        compose.onNodeWithText("https://lists.example/new.txt").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.blocklist_downloading)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.blocklist_failed)).assertIsDisplayed()
    }

    @Test
    fun `joins the lists and the add row into one segmented group`() {
        show(ProtectionUiState(sources = listOf(downloaded), resolver = RESOLVER, loaded = true))

        val list = compose.onNodeWithText(DEFAULT_LIST_NAME).getBoundsInRoot()
        val add = compose.onNodeWithText(string(R.string.add_blocklist)).getBoundsInRoot()
        assertEquals(ListItemDefaults.SegmentedGap, add.top - list.bottom)
        assertEquals(true to false, corners(DEFAULT_LIST_NAME))
        assertEquals(false to true, corners(string(R.string.add_blocklist)))
    }

    @Test
    fun `adds a list only from an https URL, with an optional name`() {
        show(ProtectionUiState(sources = listOf(downloaded), resolver = RESOLVER, loaded = true))
        compose.onNodeWithText(string(R.string.add_blocklist)).performClick()

        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("http://lists.example/a.txt")
        compose.onNodeWithText(string(R.string.url_invalid)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.add)).onLast().assertIsNotEnabled()

        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(" https://lists.example/a.txt ")
        compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("Mine")
        compose.onAllNodesWithText(string(R.string.add)).onLast().assertIsEnabled().performClick()

        assertEquals(listOf("https://lists.example/a.txt" to "Mine"), addedSources)
        compose.onAllNodesWithText(string(R.string.url)).assertCountEquals(0)
    }

    @Test
    fun `asks before removing a list`() {
        show(ProtectionUiState(sources = listOf(downloaded), resolver = RESOLVER, loaded = true))

        compose.onNodeWithContentDescription(string(R.string.remove_named, DEFAULT_LIST_NAME)).performClick()
        compose.onNodeWithText(string(R.string.remove_blocklist_description)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cancel)).performClick()
        assertTrue(removedSources.isEmpty())

        compose.onNodeWithContentDescription(string(R.string.remove_named, DEFAULT_LIST_NAME)).performClick()
        compose.onNodeWithText(string(R.string.remove)).performClick()
        assertEquals(listOf(DEFAULT_LIST_URL), removedSources)
    }

    @Test
    fun `adds a rule only for a domain, blocking unless allow is chosen`() {
        show(ProtectionUiState(resolver = RESOLVER, loaded = true))
        compose.onNodeWithText(string(R.string.add_rule)).performClick()

        compose.onNode(hasSetTextAction()).performTextReplacement("localhost")
        compose.onNodeWithText(string(R.string.domain_invalid)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.add)).onLast().assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextReplacement("Ads.Example")
        compose.onAllNodesWithText(string(R.string.add)).onLast().performClick()

        compose.onNodeWithText(string(R.string.add_rule)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("safe.example")
        compose.onNodeWithText(string(R.string.allow)).performClick()
        compose.onAllNodesWithText(string(R.string.add)).onLast().performClick()

        assertEquals(listOf("ads.example" to Action.Block, "safe.example" to Action.Allow), addedRules)
    }

    @Test
    fun `removes a rule at once`() {
        show(ProtectionUiState(rules = listOf(Rule("ads.example", Action.Block, 1)), resolver = RESOLVER, loaded = true))

        compose.onNodeWithText("ads.example").assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.remove_named, "ads.example")).performClick()

        assertEquals(listOf("ads.example"), removedRules)
    }

    @Test
    fun `changes the upstream DNS only to an https URL`() {
        show(ProtectionUiState(resolver = RESOLVER, loaded = true))
        compose.onNodeWithText(RESOLVER).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.doh_url)).performClick()

        compose.onNode(hasSetTextAction()).performTextReplacement("dns.google")
        compose.onNodeWithText(string(R.string.url_invalid)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.save)).assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextReplacement("https://dns.google/dns-query")
        compose.onNodeWithText(string(R.string.save)).performClick()

        assertEquals(listOf("https://dns.google/dns-query"), resolvers)
    }

    private fun corners(text: String): Pair<Boolean, Boolean> {
        val image = compose.onNodeWithText(text).captureToImage().asAndroidBitmap()
        val container = image.getPixel(CORNER_INSET, image.height / 2)
        return (image.getPixel(CORNER_INSET, CORNER_INSET) != container) to
            (image.getPixel(CORNER_INSET, image.height - 1 - CORNER_INSET) != container)
    }

    private fun show(initial: ProtectionUiState) {
        state = initial
        compose.setContent {
            AdblockTheme {
                ProtectionScreen(
                    uiState = state,
                    snackbarHostState = null,
                    onAddSource = { url, name -> addedSources += url to name },
                    onRemoveSource = { removedSources += it },
                    onAddRule = { host, action -> addedRules += host to action },
                    onRemoveRule = { removedRules += it },
                    onResolverChange = { resolvers += it }
                )
            }
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val CORNER_INSET = 3
        const val RESOLVER = "https://cloudflare-dns.com/dns-query"
    }
}
