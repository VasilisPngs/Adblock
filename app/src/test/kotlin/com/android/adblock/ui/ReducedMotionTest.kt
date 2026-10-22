package com.android.adblock.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.adblock.R
import com.android.adblock.data.Appearance
import com.android.adblock.data.SelfUpdate
import com.android.adblock.data.ThemeMode
import com.android.adblock.ui.theme.AdblockTheme
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
    fun `switches screens at once when animations are removed`() {
        show()

        settlesAtOnce { compose.onNodeWithText(context.getString(R.string.settings)).performClick() }
        compose.onAllNodesWithText(context.getString(R.string.dns_over_https)).assertCountEquals(0)
        settlesAtOnce { compose.runOnIdle { backDispatcher.onBackPressed() } }
        compose.onAllNodesWithText(context.getString(R.string.theme)).assertCountEquals(0)
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
                DnsScreen(
                    uiState = DnsUiState(url = SERVER),
                    snackbarHostState = remember { SnackbarHostState() },
                    onEnabledChange = {},
                    onUrlChange = {},
                    onPrivateDns = {},
                    onVpnSettings = {},
                    appearance = Appearance(ThemeMode.System, dynamicColor = false),
                    onThemeChange = {},
                    onDynamicColorChange = {},
                    selfUpdate = SelfUpdate.Idle,
                    onUpdateApp = {}
                )
            }
        }
    }

    private companion object {
        const val SERVER = "https://adblock.example.workers.dev/dns-query/0123abcd"
        const val SETTLE_MILLIS = 80L
        val AnimationsRemoved = object : MotionDurationScale {
            override val scaleFactor = 0f
        }
    }
}
