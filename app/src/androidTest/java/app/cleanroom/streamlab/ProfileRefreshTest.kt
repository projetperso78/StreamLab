package app.cleanroom.streamlab

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.data.ProfileType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileRefreshTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun openSourcesFromHome() {
        rule.onNodeWithTag("nav-more").performClick()
        rule.onNodeWithText("Sources & listes").performClick()
    }


    @Test fun explicitRefreshButtonLoadsRemotePlaylist() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val name = "Refresh QA ${System.currentTimeMillis()}"
        val m3u = "https://httpbin.org/base64/I0VYVE0zVQojRVhUSU5GOi0xIHR2Zy1pZD0iYmJiLnFhIiB0dmctbmFtZT0iQmlnIEJ1Y2sgQnVubnkgUUEiIGdyb3VwLXRpdGxlPSJRQSBJbnRlcm5ldCIsUHVibGljIFFBIEhMUwpodHRwczovL3Rlc3Qtc3RyZWFtcy5tdXguZGV2L3gzNnhoenoveDM2eGh6ei5tM3U4Cg=="
        val id = runBlocking { app.repository.createProfile(name, ProfileType.M3U_URL, m3u) }
        try {
            assertEquals(0, runBlocking { app.repository.dao.channelsForProfile(id).size })
            rule.waitForIdle()
            rule.onNodeWithText("Gérer mes sources ▼").performScrollTo().performClick()
            rule.onNodeWithTag("profile-search").performTextReplacement(name)
            rule.waitUntil(10_000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("profile-refresh-$id").assertExists().performSemanticsAction(SemanticsActions.OnClick)
            rule.waitUntil(60_000) {
                runBlocking { app.repository.dao.channelsForProfile(id).size == 1 }
            }
            assertEquals(1, runBlocking { app.repository.dao.channelsForProfile(id).size })
        } finally {
            runBlocking { app.repository.deleteProfile(id) }
        }
    }
}
