package com.vasilispngs.adblock.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.ui.theme.AdblockTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReducedMotionTest {
    @get:Rule
    val compose = createComposeRule(ComposeUiTestConfig(effectContext = AnimationsRemoved))

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Test
    fun `switches destinations at once when animations are removed`() {
        show()

        settlesAtOnce {
            compose.onNode(hasText(context.getString(R.string.settings)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .performClick()
        }
        compose.onAllNodesWithText(HOME_CONTENT).assertCountEquals(0)
        settlesAtOnce { compose.runOnIdle { backDispatcher.onBackPressed() } }
        compose.onAllNodesWithText(SETTINGS_CONTENT).assertCountEquals(0)
    }

    private fun settlesAtOnce(action: () -> Unit) {
        compose.waitForIdle()
        val start = compose.mainClock.currentTime
        action()
        compose.waitForIdle()
        assertTrue(compose.mainClock.currentTime - start <= SETTLE_MILLIS)
    }

    private fun show() {
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            AdblockTheme {
                AdblockNavigation(
                    snackbarHostState = remember { SnackbarHostState() },
                    home = { _, _, _ -> Text(HOME_CONTENT) },
                    protection = { _, _, _ -> Text(PROTECTION_CONTENT) },
                    settings = { _, _, _ -> Text(SETTINGS_CONTENT) }
                )
            }
        }
    }

    private companion object {
        const val HOME_CONTENT = "home content"
        const val PROTECTION_CONTENT = "protection content"
        const val SETTINGS_CONTENT = "settings content"
        const val SETTLE_MILLIS = 80L
        val AnimationsRemoved = object : MotionDurationScale {
            override val scaleFactor = 0f
        }
    }
}
