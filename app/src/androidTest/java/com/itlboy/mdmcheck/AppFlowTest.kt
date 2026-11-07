package com.itlboy.mdmcheck

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks the app like a user: Check → result → detail → back → check again. No screen scrolls. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val anyVerdict = hasText("Device is clean") or hasText("Worth a closer look") or hasText("Device is managed")

    @Test fun homeShowsTitleAndVersion() {
        rule.onNodeWithText("Cozy MDM Check").assertIsDisplayed()
        rule.onNodeWithText("Check").assertIsDisplayed()
        rule.onNodeWithText("v${BuildConfig.VERSION_NAME} · ", substring = true).assertIsDisplayed()
    }

    @Test fun infoSheetOpensAndCloses() {
        rule.onNodeWithContentDescription("What is the setup date?").performClick()
        rule.onNodeWithText("Got it").assertIsDisplayed().performClick()
        rule.waitUntilDoesNotExist(hasText("Got it"), 5_000)
    }

    @Test fun fullFlow() {
        rule.onNodeWithText("Check").performClick()
        rule.waitUntilAtLeastOneExists(anyVerdict, 10_000)

        // Everything fits on screen without scrolling.
        for (title in listOf("Management signs", "Device info", "Device history", "Other security", "Check again")) {
            rule.onNodeWithText(title).assertIsDisplayed()
        }

        rule.onNodeWithText("Management signs").performClick()
        rule.onNodeWithText("Device admin apps").assertIsDisplayed()
        rule.onNodeWithText("Blocked features").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()

        rule.onNodeWithText("Device history").performClick()
        rule.onNodeWithText("Setup date").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()

        rule.onNodeWithText("Check again").performClick()
        rule.waitUntilAtLeastOneExists(anyVerdict, 10_000)
    }
}
