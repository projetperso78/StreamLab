package app.cleanroom.streamlab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KidsModeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication

    @Test fun parentLocksTheAppToFavouritesAndOnlyThePinUnlocksIt() {
        val profile = runBlocking { app.repository.importDemoFixture("Kids mode QA") }
        try {
            val favourite = runBlocking { app.repository.dao.channelsForProfile(profile) }.first()
            runBlocking { app.repository.toggleFavorite(favourite.id) }
            // A second, non-favourite item that kids mode must not show.
            val hiddenName = "Radio cachée QA"
            runBlocking { app.repository.addRadioStation("ZZ", hiddenName, "https://stream.example.org/hidden.mp3", null, "kids-qa-hidden") }
            val hidden = object { val name = hiddenName }

            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Réglages").performClick()
            rule.onNodeWithTag("settings-kids").performScrollTo().performClick()
            rule.onNodeWithTag("kids-new-pin").performTextInput("2468")
            rule.onNodeWithTag("kids-confirm-pin").performTextInput("2469")
            rule.onNodeWithTag("kids-start").assertIsNotEnabled()
            rule.onNodeWithText("Les deux codes ne correspondent pas.").assertIsDisplayed()
            rule.onNodeWithTag("kids-confirm-pin").performTextReplacement("2468")
            rule.onNodeWithTag("kids-start").performClick()

            rule.waitUntil(5_000) { rule.onAllNodesWithTag("kids-mode").fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(10_000) { rule.onAllNodesWithText(favourite.name).fetchSemanticsNodes().isNotEmpty() }
            assertTrue("non-favourite content must be hidden", rule.onAllNodesWithText(hidden.name).fetchSemanticsNodes().isEmpty())
            rule.onAllNodesWithTag("nav-more").fetchSemanticsNodes().let { assertTrue("no navigation in kids mode", it.isEmpty()) }

            // Back does not leave: it asks for the parent code. A wrong code is refused.
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.onNodeWithTag("kids-pin").performTextInput("1111")
            rule.onNodeWithTag("kids-unlock").performClick()
            rule.onNodeWithText("Code incorrect.").assertIsDisplayed()
            rule.onNodeWithTag("kids-mode").assertExists()

            rule.onNodeWithTag("kids-pin").performTextInput("2468")
            rule.onNodeWithTag("kids-unlock").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("kids-mode").fetchSemanticsNodes().isEmpty() }
            assertFalse(KidsMode.active.value)
            rule.onNodeWithTag("nav-more").assertIsDisplayed()
        } finally {
            rule.runOnUiThread { KidsMode.active.value = false }
            app.getSharedPreferences("streamlab_kids", 0).edit().clear().commit()
            runBlocking {
                app.repository.deleteProfile(profile)
                app.repository.dao.profileByName("Radio · ZZ")?.let { app.repository.deleteProfile(it.id) }
            }
        }
    }
}
