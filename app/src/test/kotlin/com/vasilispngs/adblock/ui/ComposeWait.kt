package com.vasilispngs.adblock.ui

import androidx.compose.ui.test.junit4.ComposeTestRule

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
