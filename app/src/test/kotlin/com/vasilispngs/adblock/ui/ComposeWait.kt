package com.vasilispngs.adblock.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.robolectric.Shadows.shadowOf

private const val WAIT_MILLIS = 5_000L
private const val STEP_MILLIS = 16L

internal fun ComposeTestRule.awaitCondition(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + WAIT_MILLIS
    while (true) {
        waitForIdle()
        if (condition()) return
        check(System.currentTimeMillis() < deadline) { "Condition still not satisfied after $WAIT_MILLIS ms" }
        mainClock.advanceTimeBy(STEP_MILLIS)
        Thread.sleep(STEP_MILLIS)
    }
}

internal fun ComposeTestRule.drainingMainLooper(): TestRule =
    RuleChain.outerRule(
        object : ExternalResource() {
            override fun after() {
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    ).around(this)
