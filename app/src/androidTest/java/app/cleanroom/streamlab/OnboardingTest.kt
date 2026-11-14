package app.cleanroom.streamlab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun markDone() {
        context.getSharedPreferences("streamlab_onboarding", 0).edit().putBoolean("done", true).putInt("step", 0).commit()
    }

    @Test fun firstLaunchPicksLanguageThenOpensRadiosOfTheCountry() {
        context.getSharedPreferences("streamlab_onboarding", 0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.onNodeWithTag("onboarding").assertIsDisplayed()
            rule.onNodeWithTag("onboarding-language-fr").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("onboarding-radio").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("onboarding-radio").performClick()
            rule.onNodeWithTag("onboarding").assertDoesNotExist()
            rule.onNodeWithTag("nav-radio").assertIsSelected()
            // Lands directly on the stations of the detected country (no country list first).
            rule.waitUntil(20_000) { rule.onAllNodesWithTag("radio-back-countries").fetchSemanticsNodes().isNotEmpty() }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.onNodeWithTag("onboarding").assertDoesNotExist()
        }
    }
}
