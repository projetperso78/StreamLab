package app.cleanroom.streamlab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.ShareCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication

    private fun cleanup() = runBlocking {
        app.repository.dao.profileByName("Radio · MA")?.let { app.repository.deleteProfile(it.id) }
    }

    @Test fun favouriteRadioIsSharedAsQrAndAFriendCanImportIt() {
        cleanup()
        try {
            val id = runBlocking {
                app.repository.addRadioStation("MA", "Radio Partage QA", "https://stream.example.org/partage.mp3", null, "share-qa")
            }
            runBlocking { app.repository.toggleFavorite(id) }
            val code = runBlocking { ShareCode.encode(app.repository.shareBundle()) }

            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Partager avec un ami").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("share-qr").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("share-qr").assertIsDisplayed()

            // The friend's phone: the radio is not there yet, then the pasted code adds it as a favourite.
            cleanup()
            rule.onNodeWithTag("share-paste").performTextInput(code)
            rule.onNodeWithTag("share-import").performClick()
            // The import creates the country profile first, then the station and its favourite flag: wait for the end state.
            rule.waitUntil(10_000) {
                runBlocking { app.repository.channels.first().any { it.name == "Radio Partage QA" && it.favorite } }
            }
            val imported = runBlocking { app.repository.channels.first().single { it.name == "Radio Partage QA" } }
            assertTrue("shared radio must arrive as a favourite", imported.favorite)
            assertEquals("MA", runBlocking { app.repository.dao.profile(imported.profileId)?.name?.removePrefix("Radio · ") })
        } finally {
            cleanup()
        }
    }
}
