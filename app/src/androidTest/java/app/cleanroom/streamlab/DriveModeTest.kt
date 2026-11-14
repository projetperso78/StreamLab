package app.cleanroom.streamlab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveModeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun driveModeResumesLastPlayedItemAndSetsSleepTimer() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val profile = runBlocking { app.repository.importDemoFixture("Drive mode QA") }
        try {
            val row = runBlocking { app.repository.dao.channelsForProfile(profile).first() }
            runBlocking { app.repository.markPlayed(row.id) }

            rule.onNodeWithTag("apps-grid").performScrollToNode(hasTestTag("passenger-card-drive"))
            rule.onNodeWithTag("passenger-card-drive").performClick()
            rule.onNodeWithTag("drive-mode").assertIsDisplayed()
            rule.waitUntil(15_000) { rule.onAllNodesWithText(row.name).fetchSemanticsNodes().isNotEmpty() }

            rule.onNodeWithTag("drive-sleep-15").performClick()
            rule.onNodeWithTag("drive-sleep-left").assertIsDisplayed()
            rule.onNodeWithTag("drive-sleep-0").performClick()
            rule.onNodeWithTag("drive-sleep-left").assertDoesNotExist()

            rule.onNodeWithTag("drive-exit").performClick()
            rule.onNodeWithTag("apps-grid").assertIsDisplayed()
        } finally {
            rule.runOnUiThread { }
            runBlocking { app.repository.deleteProfile(profile) }
        }
    }
}
