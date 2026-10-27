package com.vasilispngs.adblock.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.textAsString
import androidx.test.uiautomator.uiAutomator
import org.junit.Rule
import org.junit.Test

internal const val PACKAGE_NAME = "com.vasilispngs.adblock"

class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun screens() = rule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()
        uiAutomator {
            onElement { contentDescription?.toString() == "Settings" }.click()
            onElement { textAsString() == "Theme" }.click()
            onElement { textAsString() == "Cancel" }.click()
            onElement { contentDescription?.toString() == "Back" }.click()
        }
    }
}
