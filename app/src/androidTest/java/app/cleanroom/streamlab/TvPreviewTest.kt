package app.cleanroom.streamlab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvPreviewTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun selectPreviewFullscreenAndReturnWithoutLosingChannel() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val profile = runBlocking { app.repository.importDemoFixture("TV split QA") }
        try {
            val row = runBlocking { app.repository.dao.channelsForProfile(profile).first() }
            rule.onNodeWithTag("nav-live").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tv-channels").fetchSemanticsNodes().isNotEmpty() }
            val channelTag = "tv-channel-${row.id}"
            rule.onNodeWithTag("tv-channels").performScrollToNode(hasTestTag(channelTag))
            rule.onNodeWithTag(channelTag).assertExists().performClick()
            rule.onNodeWithTag("tv-preview").performClick()
            rule.onNodeWithTag("tv-channels").assertDoesNotExist()
            rule.onNodeWithTag("nav-apps").assertDoesNotExist()
            rule.onNodeWithTag("tv-back").performClick()
            rule.onNodeWithTag("tv-channels").assertExists()
            rule.onAllNodesWithText(row.name).onFirst().assertExists()
            rule.onNodeWithTag("nav-apps").performClick()
            rule.onNodeWithTag("apps-grid").assertExists()
        } finally { runBlocking { app.repository.deleteProfile(profile) } }
    }
}
