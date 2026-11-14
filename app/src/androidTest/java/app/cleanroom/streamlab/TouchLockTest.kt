package app.cleanroom.streamlab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TouchLockTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun lockedFullscreenTvIgnoresTouchesUntilLongPressOnPadlock() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val profile = runBlocking { app.repository.importDemoFixture("Touch lock QA") }
        try {
            val row = runBlocking { app.repository.dao.channelsForProfile(profile).first() }
            rule.onNodeWithTag("nav-live").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tv-channels").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tv-channels").performScrollToNode(hasTestTag("tv-channel-${row.id}"))
            rule.onNodeWithTag("tv-channel-${row.id}").performClick()
            rule.onNodeWithTag("tv-fullscreen").performClick()

            rule.onNodeWithTag("player-lock").performClick()
            rule.onNodeWithTag("player-touch-lock").assertIsDisplayed()

            // A tap on "back" while locked must not leave fullscreen.
            rule.onNodeWithTag("tv-back").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("tv-channels").assertDoesNotExist()
            rule.onNodeWithTag("player-touch-lock").assertIsDisplayed()

            rule.onNodeWithTag("player-unlock").performTouchInput { longClick() }
            rule.waitForIdle()
            rule.onNodeWithTag("player-touch-lock").assertDoesNotExist()

            rule.onNodeWithTag("tv-back").performClick()
            rule.onNodeWithTag("tv-channels").assertExists()
        } finally {
            runBlocking { app.repository.deleteProfile(profile) }
        }
    }
}
