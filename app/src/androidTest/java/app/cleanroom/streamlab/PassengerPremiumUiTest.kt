package app.cleanroom.streamlab

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

/** Passenger home acceptance: essential targets must be real clickable Compose elements. */
@RunWith(AndroidJUnit4::class)
class PassengerPremiumUiTest {
    @get:Rule val rule = createAndroidComposeRule<CarServicesActivity>()

    @Test fun passengerHomeKeepsLanguageSelectionInsideSettings() {
        rule.onNodeWithTag("passenger-grid").assertExists()
        rule.onNodeWithTag("passenger-card-live").assertExists()
        rule.onNodeWithTag("passenger-grid", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("passenger-card-radio"))
        rule.onNodeWithTag("passenger-card-radio").assertExists()
        rule.onNodeWithTag("passenger-language-fr").assertDoesNotExist()
        rule.onNodeWithTag("passenger-language-en").assertDoesNotExist()
        rule.onNodeWithTag("passenger-language-ar").assertDoesNotExist()
        rule.onNodeWithTag("passenger-language-system").assertDoesNotExist()
        rule.onNodeWithTag("passenger-nav-more").performClick()
        rule.onNodeWithTag("passenger-settings").assertExists()
        rule.onNodeWithTag("passenger-language-fr").assertExists()
        rule.onNodeWithTag("passenger-language-en").assertExists()
        rule.onNodeWithTag("passenger-language-ar").assertExists()
        rule.onNodeWithTag("passenger-language-system").assertExists()
    }

    @Test fun passengerCanSelectSystemLanguageOnlyInsideSettings() {
        rule.onNodeWithTag("passenger-language-system").assertDoesNotExist()
        rule.onNodeWithTag("passenger-nav-more").performClick()
        try {
            rule.onNodeWithTag("passenger-language-system").performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("passenger-language-system").assertIsSelected()
            assertEquals(
                "system",
                rule.activity.getSharedPreferences("streamlab_language_preferences", 0)
                    .getString("selected_language", null),
            )
        } finally {
            rule.runOnUiThread { StreamLabLocale.select(rule.activity, "fr") }
        }
    }

    @Test fun passengerLanguageChangeKeepsSettingsOpenAndUsesArabicRtl() {
        rule.onNodeWithTag("passenger-nav-more").performClick()
        rule.onNodeWithTag("passenger-language-ar").performScrollTo().performClick()
        rule.waitForIdle()
        try {
            rule.onNodeWithTag("passenger-settings").assertExists()
            rule.onNodeWithText("الإعدادات").assertExists()
            rule.runOnIdle { assertEquals("ar", StreamLabLocale.current(rule.activity)) }
        } finally {
            rule.runOnIdle { StreamLabLocale.select(rule.activity, "fr") }
        }
    }
}
