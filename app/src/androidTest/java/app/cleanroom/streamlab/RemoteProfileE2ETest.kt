package app.cleanroom.streamlab

import android.content.ComponentName
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.cleanroom.streamlab.player.PlaybackService
import java.util.concurrent.TimeUnit
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.data.ProfileType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteProfileE2ETest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun openSourcesFromHome() {
        rule.onNodeWithTag("nav-more").performClick()
        rule.onNodeWithText("Sources & listes").performClick()
    }


    @Test fun realHttpsM3uXmltvAndPlaybackPath() {
        val name = "QA remote ${System.currentTimeMillis()}"
        val m3u = "https://httpbin.org/base64/I0VYVE0zVQojRVhUSU5GOi0xIHR2Zy1pZD0iYmJiLnFhIiB0dmctbmFtZT0iQmlnIEJ1Y2sgQnVubnkgUUEiIGdyb3VwLXRpdGxlPSJRQSBJbnRlcm5ldCIsUHVibGljIFFBIEhMUwpodHRwczovL3Rlc3Qtc3RyZWFtcy5tdXguZGV2L3gzNnhoenoveDM2eGh6ei5tM3U4Cg=="
        val xmltv = "https://httpbin.org/base64/PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz48dHY+PGNoYW5uZWwgaWQ9ImJiYi5xYSI+PGRpc3BsYXktbmFtZT5CaWcgQnVjayBCdW5ueSBRQTwvZGlzcGxheS1uYW1lPjwvY2hhbm5lbD48cHJvZ3JhbW1lIHN0YXJ0PSIyMDI2MTAwMzAwMDAwMCArMDAwMCIgc3RvcD0iMjA5OTEyMzEyMzU5NTkgKzAwMDAiIGNoYW5uZWw9ImJiYi5xYSI+PHRpdGxlPlFBIEludGVybmV0IFByb2dyYW1tZTwvdGl0bGU+PC9wcm9ncmFtbWU+PC90dj4="
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val profileId = runBlocking {
            app.repository.createProfile(name, ProfileType.M3U_URL, m3u, epg = xmltv)
        }
        var mediaController: MediaController? = null
        try {
            rule.waitForIdle()
            rule.onNodeWithText("Gérer mes sources ▼").performScrollTo().performClick()
            rule.onNodeWithTag("profile-search").performTextReplacement(name)
            rule.waitUntil(10_000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("profile-refresh-$profileId").assertExists().performSemanticsAction(SemanticsActions.OnClick)
            rule.waitUntil(60_000) {
                runBlocking { app.repository.dao.channelsForProfile(profileId).size == 1 }
            }
            rule.onNodeWithTag("profile-guide-$profileId").assertExists().performSemanticsAction(SemanticsActions.OnClick)
            rule.waitUntil(60_000) {
                runBlocking {
                    app.repository.dao.guide(profileId, 0L).first().any { it.title == "QA Internet Programme" }
                }
            }
            rule.waitUntil(30_000) {
                rule.onAllNodesWithText("QA Internet Programme").fetchSemanticsNodes().isNotEmpty()
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            mediaController = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java)),
            ).setApplicationLooper(Looper.getMainLooper()).buildAsync().get(10, TimeUnit.SECONDS)
            rule.onNodeWithTag("nav-live").performClick()
            // The selected channel's name is also shown under the preview, so target the list row itself.
            val channelId = runBlocking { app.repository.dao.channelsForProfile(profileId).single().id }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tv-channels").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tv-channels").performScrollToNode(hasTestTag("tv-channel-$channelId"))
            rule.onNodeWithTag("tv-channel-$channelId").assertExists().performClick()
            var observedPlayer = "no player state yet"
            try {
                rule.waitUntil(30_000) {
                    var ready = false
                    rule.runOnUiThread {
                        val player = requireNotNull(mediaController)
                        val types = player.currentTracks.groups.map { it.type }.toSet()
                        observedPlayer = "state=${player.playbackState}, playing=${player.isPlaying}, tracks=$types, error=${player.playerError?.errorCodeName}"
                        ready = player.playbackState == Player.STATE_READY && player.isPlaying &&
                            C.TRACK_TYPE_AUDIO in types && C.TRACK_TYPE_VIDEO in types
                    }
                    ready
                }
            } catch (cause: androidx.compose.ui.test.ComposeTimeoutException) {
                val ui = runCatching {
                    val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                    buildString {
                        fun visit(node: androidx.compose.ui.semantics.SemanticsNode, depth: Int) {
                            append(" ".repeat(depth)).append(node.config.toString()).append('\n')
                            if (length >= 9000 || depth >= 12) return
                            node.children.forEach { visit(it, depth + 1) }
                        }
                        visit(root, 0)
                    }
                }.getOrDefault("Compose semantics tree unavailable")
                throw AssertionError(
                    "Public HLS never reached a playing state with audio+video. Player: $observedPlayer. Current UI: ${ui.take(6500)}",
                    cause,
                )
            }
            rule.onNodeWithTag("tv-preview").assertExists().performClick()
            rule.onNodeWithTag("tv-back").assertExists().performClick()
            rule.onNodeWithTag("tv-channels").assertExists()
        } finally {
            rule.runOnUiThread { mediaController?.release() }
            runBlocking {
                app.repository.profiles.first().firstOrNull { it.name == name }?.let { app.repository.deleteProfile(it.id) }
            }
        }
    }
}
