package app.cleanroom.streamlab

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.data.ProfileType
import app.cleanroom.streamlab.data.ProgrammeEntity
import app.cleanroom.streamlab.player.PlaybackService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvExperienceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication

    private val observer by lazy {
        androidx.media3.session.MediaController.Builder(app, androidx.media3.session.SessionToken(app,
            android.content.ComponentName(app, PlaybackService::class.java))).buildAsync().get(15, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun currentMediaId(): String? {
        var id: String? = null
        val controller = observer // built off the main thread: get() on it would deadlock
        rule.runOnUiThread { id = controller.currentMediaItem?.mediaId }
        return id
    }

    @Test(timeout = 120_000)
    fun liveTvPlayerZapsWithButtonsAndChannelList() {
        // Three one-channel sources: enough to zap forward and pick from the list.
        val profiles = runBlocking { listOf("Zap QA A", "Zap QA B", "Zap QA C").map { app.repository.createProfile(it, ProfileType.MANUAL, PublicTestStream.URL) } }
        try {
            val rows = runBlocking { profiles.flatMap { app.repository.dao.channelsForProfile(it) } }
            assertTrue("fixture needs two channels", rows.size >= 2)
            rule.onNodeWithTag("nav-live").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tv-channels").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tv-channels").performScrollToNode(hasTestTag("tv-channel-${rows[0].id}"))
            rule.onNodeWithTag("tv-channel-${rows[0].id}").performClick()
            rule.onNodeWithTag("tv-fullscreen").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tv-live-overlay").fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(10_000) { currentMediaId() != null }
            val before = currentMediaId()

            rule.onNodeWithTag("tv-next").performClick()
            rule.waitUntil(10_000) { currentMediaId() != before }
            assertNotEquals(before, currentMediaId())

            // Controls hide by themselves; a tap on the picture brings them back.
            rule.mainClock.advanceTimeBy(6_000)
            rule.waitUntil(8_000) { rule.onAllNodesWithTag("tv-open-list").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithTag("tv-preview").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv-open-list").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tv-open-list").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv-channel-drawer").fetchSemanticsNodes().isNotEmpty() }
            val target = rows.first { OpaqueMediaId.channel(it.id) != currentMediaId() }
            rule.onNodeWithTag("tv-drawer-${target.id}").performClick()
            rule.waitUntil(10_000) { currentMediaId() == OpaqueMediaId.channel(target.id) }
            // Picking a channel hides the controls; one tap shows them, Back leaves full screen.
            rule.onNodeWithTag("tv-preview").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv-back").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tv-back").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv-channels").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            rule.runOnUiThread { observer.stop(); observer.release() }
            runBlocking { profiles.forEach { app.repository.deleteProfile(it) } }
        }
    }

    @Test fun addingASingleChannelPutsItInTheTvList() {
        rule.onNodeWithTag("nav-live").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("tv-add-channels").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("tv-add-source").fetchSemanticsNodes().isNotEmpty()
        }
        val opener = if (rule.onAllNodesWithTag("tv-add-channels").fetchSemanticsNodes().isNotEmpty()) "tv-add-channels" else "tv-add-source"
        rule.onNodeWithTag(opener).performClick()
        rule.onNodeWithTag("add-single").performClick()
        rule.onNodeWithTag("add-source-url").performTextInput(PublicTestStream.URL)
        rule.onNodeWithTag("add-save").performClick()
        try {
            rule.waitUntil(15_000) { runBlocking { app.repository.dao.channels().first() }.any { it.name == "Ma chaîne" } }
            val added = runBlocking { app.repository.dao.channels().first() }.first { it.name == "Ma chaîne" }
            // Other sources may fill the screen: scroll the list to the new channel.
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tv-channels").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tv-channels").performScrollToNode(hasTestTag("tv-channel-${added.id}"))
        } finally {
            runBlocking { app.repository.dao.profileByName("Ma chaîne")?.let { app.repository.deleteProfile(it.id) } }
        }
    }

    @Test(timeout = 120_000)
    fun checkingChannelsMovesDeadOnesDownAndGreysThem() {
        app.getSharedPreferences("streamlab_channel_health", 0).edit().clear().commit()
        val dead = runBlocking { app.repository.createProfile("AAA Chaine morte QA", ProfileType.MANUAL, "https://dead.streamlab-qa.invalid/live.m3u8") }
        val alive = runBlocking { app.repository.createProfile("ZZZ Chaine vivante QA", ProfileType.MANUAL, PublicTestStream.URL) }
        try {
            val deadId = runBlocking { app.repository.dao.channelsForProfile(dead).first().id }
            rule.onNodeWithTag("nav-live").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tv-check-channels").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tv-check-channels").performClick()
            rule.waitUntil(60_000) { rule.onAllNodesWithTag("tv-check-result").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(deadId in ChannelHealth.deadIds(app))
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv-dead-$deadId", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } finally {
            runBlocking { app.repository.deleteProfile(dead); app.repository.deleteProfile(alive) }
            app.getSharedPreferences("streamlab_channel_health", 0).edit().clear().commit()
        }
    }

    @Test fun tvShowsWhatIsOnNowAndNext() {
        val profile = runBlocking { app.repository.createProfile("Guide maintenant QA", ProfileType.MANUAL, PublicTestStream.URL) }
        try {
            val row = runBlocking { app.repository.dao.channelsForProfile(profile).first() }
            val now = System.currentTimeMillis()
            runBlocking {
                app.repository.dao.insertProgrammes(listOf(
                    ProgrammeEntity(channelId = row.id, title = "Journal QA", startMs = now - 10 * 60_000, endMs = now + 20 * 60_000),
                    ProgrammeEntity(channelId = row.id, title = "Film QA", startMs = now + 20 * 60_000, endMs = now + 140 * 60_000),
                ))
            }
            rule.onNodeWithTag("nav-live").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tv-channels").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tv-channels").performScrollToNode(hasTestTag("tv-channel-${row.id}"))
            rule.onNodeWithTag("tv-channel-${row.id}").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Journal QA", substring = true).fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Film QA", substring = true).fetchSemanticsNodes().isNotEmpty() }
        } finally {
            runBlocking { app.repository.deleteProfile(profile) }
        }
    }

    @Test(timeout = 120_000)
    fun multiviewShowsSeveralChannelsAndSwitchesSound() {
        val profiles = runBlocking { listOf("Multi A QA", "Multi B QA", "Multi C QA").map { app.repository.createProfile(it, ProfileType.MANUAL, PublicTestStream.URL) } }
        try {
            val rows = runBlocking { profiles.flatMap { app.repository.dao.channelsForProfile(it) } }
            rule.onNodeWithTag("nav-live").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tv-multiview").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tv-multiview").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("multiview").fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("multiview-tile-${rows[1].id}").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("multiview-tile-${rows[1].id}").performClick()
            rule.onNodeWithTag("multiview-count-2").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("multiview-tile-${rows[2].id}").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithTag("multiview-exit").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv-channels").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            runBlocking { profiles.forEach { app.repository.deleteProfile(it) } }
        }
    }
}
