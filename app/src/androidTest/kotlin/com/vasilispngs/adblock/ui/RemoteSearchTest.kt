package com.vasilispngs.adblock.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.platform.app.InstrumentationRegistry
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.LoggedQuery
import com.vasilispngs.adblock.ui.theme.AdblockTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class)
class RemoteSearchTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val keyboards = AtomicInteger()
    private val keys = AtomicInteger()

    @Before
    fun leaveTouchMode() {
        instrumentation.setInTouchMode(false)
        instrumentation.waitForIdleSync()
    }

    @After
    fun enterTouchMode() {
        instrumentation.setInTouchMode(true)
    }

    @Test
    fun opensTheKeyboardOnlyAfterOk() {
        compose.setContent {
            InterceptPlatformTextInput(
                interceptor = { request, nextHandler ->
                    keyboards.incrementAndGet()
                    try {
                        nextHandler.startInputMethod(request)
                    } finally {
                        keyboards.decrementAndGet()
                    }
                }
            ) {
                AdblockTheme {
                    val log = remember { flowOf(PagingData.empty<LoggedQuery>()) }.collectAsLazyPagingItems()
                    HomeScreen(
                        HomeUiState(loaded = true), log, null, {}, {}, {}, {}, {}, { _, _ -> }, {},
                        modifier = Modifier.onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) keys.incrementAndGet(); false }
                    )
                }
            }
        }
        compose.waitForIdle()

        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(0, keyboards.get())

        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(TIMEOUT_MILLIS) { keyboards.get() == 1 }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)

        compose.onAllNodes(hasSetTextAction()).onLast().performImeAction()
        compose.waitUntil(TIMEOUT_MILLIS) { keyboards.get() == 0 && compose.activity.hasWindowFocus() }
        compose.onNode(hasSetTextAction()).assertIsFocused()

        pressUntilReceived(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithText(compose.activity.getString(R.string.filter_all)).assertIsFocused()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        assertEquals(0, keyboards.get())

        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNode(hasSetTextAction()).assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(TIMEOUT_MILLIS) { keyboards.get() == 1 }
    }

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun pressUntilReceived(keyCode: Int) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        do {
            val received = keys.get()
            press(keyCode)
        } while (keys.get() == received && SystemClock.uptimeMillis() < deadline)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
