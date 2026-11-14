package app.cleanroom.streamlab

import android.content.ComponentName
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.data.ProgrammeEntity
import app.cleanroom.streamlab.jobs.ProgrammeReminder
import app.cleanroom.streamlab.player.PlaybackService
import app.cleanroom.streamlab.services.RadioCountry
import app.cleanroom.streamlab.services.RadioDirectoryGateway
import app.cleanroom.streamlab.services.RadioStation
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewFeaturesTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication

    private fun controller(): MediaController =
        MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync().get(15, TimeUnit.SECONDS)

    @Test(timeout = 120_000)
    fun nowPlayingBarShowsWhatPlaysPausesAndReopensThePlayer() {
        val profile = runBlocking { app.repository.importDemoFixture("Bar QA") }
        val controller = controller()
        try {
            val row = runBlocking { app.repository.dao.channelsForProfile(profile).first() }
            rule.runOnUiThread {
                controller.setMediaItem(MediaItem.Builder().setMediaId(OpaqueMediaId.channel(row.id)).build())
                controller.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                controller.prepare(); controller.play()
            }
            rule.waitUntil(20_000) { rule.onAllNodesWithTag("now-playing-bar").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("now-playing-title", useUnmergedTree = true).assertIsDisplayed()

            rule.onNodeWithTag("now-playing-toggle", useUnmergedTree = true).performClick()
            rule.waitUntil(10_000) { var p = true; rule.runOnUiThread { p = controller.playWhenReady }; !p }

            rule.onNodeWithTag("now-playing-bar").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("player-back").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            rule.runOnUiThread { controller.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF; controller.stop(); controller.clearMediaItems(); controller.release() }
            runBlocking { app.repository.deleteProfile(profile) }
        }
    }

    @Test fun homeOffersToContinueAStartedFilm() {
        val profile = runBlocking { app.repository.importDemoFixture("Continue QA") }
        try {
            val row = runBlocking { app.repository.dao.channelsForProfile(profile).first() }
            runBlocking {
                app.repository.markPlayed(row.id)
                app.repository.updatePlaybackProgress(row.id, 42 * 60_000L, 100 * 60_000L)
            }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("continue-${row.id}").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Il reste 58 min").assertIsDisplayed()
            rule.onNodeWithTag("continue-${row.id}").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("player-back").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            runBlocking { app.repository.deleteProfile(profile) }
        }
    }

    @Test fun liveRadioNeverShowsInContinueWatching() {
        // A live HLS radio (like RTL) exposes a short replay window; it must not look like a film.
        val radio = runBlocking { app.repository.addRadioStation("FR", "Live QA", "https://radio.example/live.m3u8", null, "live-qa") }
        val profile = runBlocking { app.repository.importDemoFixture("Continue live QA") }
        try {
            val film = runBlocking { app.repository.dao.channelsForProfile(profile).first() }
            runBlocking {
                app.repository.markPlayed(radio); app.repository.updatePlaybackProgress(radio, 50_000L, 60_000L * 3)
                app.repository.markPlayed(film.id); app.repository.updatePlaybackProgress(film.id, 42 * 60_000L, 100 * 60_000L)
            }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("continue-${film.id}").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(rule.onAllNodesWithTag("continue-$radio").fetchSemanticsNodes().isEmpty())
        } finally {
            runBlocking {
                app.repository.deleteProfile(profile)
                app.repository.dao.profileByName("Radio · FR")?.let { app.repository.deleteProfile(it.id) }
            }
        }
    }

    @Test fun guideBellSchedulesAndCancelsAReminder() {
        // A real user sees Android's notification prompt on the first bell; the test grants it up front.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val profile = runBlocking { app.repository.importDemoFixture("Guide QA") }
        try {
            val row = runBlocking { app.repository.dao.channelsForProfile(profile).first() }
            val start = System.currentTimeMillis() + 60 * 60_000L
            runBlocking { app.repository.dao.insertProgrammes(listOf(ProgrammeEntity(channelId = row.id, title = "Journal QA", startMs = start, endMs = start + 30 * 60_000L))) }
            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Guide TV").performClick()
            rule.onNodeWithText(profileName(profile)).performScrollTo().performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Journal QA").fetchSemanticsNodes().isNotEmpty() }
            val bell = rule.onAllNodesWithContentDescription("Me rappeler cette émission").fetchSemanticsNodes()
            assertTrue("a future programme offers a bell", bell.isNotEmpty())
            rule.onAllNodesWithContentDescription("Me rappeler cette émission")[0].performClick()
            assertTrue(ProgrammeReminder.isSet(app, row.id, start))
            rule.onAllNodesWithContentDescription("Annuler le rappel")[0].performClick()
            assertFalse(ProgrammeReminder.isSet(app, row.id, start))
        } finally {
            runBlocking { app.repository.deleteProfile(profile) }
        }
    }

    @Test fun reminderShowsANotificationBeforeTheProgramme() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        manager.cancelAll()
        // Starts in 2 minutes: inside the 5-minute lead, so the notification is due at once.
        val start = System.currentTimeMillis() + 2 * 60_000L
        assertTrue(ProgrammeReminder.toggle(app, 4242L, start, "Match QA", "Chaîne QA"))
        val deadline = System.currentTimeMillis() + 20_000
        var shown = false
        while (!shown && System.currentTimeMillis() < deadline) {
            shown = manager.activeNotifications.any { it.notification.extras.getString(android.app.Notification.EXTRA_TITLE)?.contains("Match QA") == true }
            if (!shown) Thread.sleep(500)
        }
        manager.cancelAll()
        assertTrue("reminder notification must be posted", shown)
        assertFalse("a fired reminder is forgotten", ProgrammeReminder.isSet(app, 4242L, start))
    }

    @Test fun trendingChipLoadsRisingStations() {
        val previous = app.radioDirectoryOverride
        app.radioDirectoryOverride = object : RadioDirectoryGateway {
            override suspend fun countries() = listOf(RadioCountry("MA", 2))
            override suspend fun stations(countryCode: String, query: String, limit: Int) = listOf(station("pop", "Classique Populaire"))
            override suspend fun trending(countryCode: String, limit: Int) = listOf(station("hot", "Radio Qui Monte"))
        }
        try {
            rule.onNodeWithTag("nav-radio").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("radio-country-MA").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("radio-country-MA").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Classique Populaire").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("radio-sort-trending").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Radio Qui Monte").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(rule.onAllNodesWithText("Classique Populaire").fetchSemanticsNodes().isEmpty())
        } finally {
            app.radioDirectoryOverride = previous
        }
    }

    @Test(timeout = 120_000)
    fun deadStreamSwitchesToItsBackupByItself() {
        // Main address cannot resolve; the second one is a public test stream.
        val id = runBlocking {
            app.repository.addRadioStation("FR", "Failover QA", "https://dead.streamlab-qa.invalid/live.mp3", null, "failover-qa",
                backups = listOf(PublicTestStream.URL))
        }
        val controller = controller()
        try {
            rule.runOnUiThread {
                controller.setMediaItem(MediaItem.Builder().setMediaId(OpaqueMediaId.channel(id)).build())
                controller.prepare(); controller.play()
            }
            rule.waitUntil(60_000) { var playing = false; rule.runOnUiThread { playing = controller.isPlaying }; playing }
        } finally {
            rule.runOnUiThread { controller.stop(); controller.clearMediaItems(); controller.release() }
            runBlocking { app.repository.dao.profileByName("Radio · FR")?.let { app.repository.deleteProfile(it.id) } }
        }
    }

    @Test fun starOnARadioKeepsItInFavorites() {
        val previous = app.radioDirectoryOverride
        app.radioDirectoryOverride = object : RadioDirectoryGateway {
            override suspend fun countries() = listOf(RadioCountry("MA", 1))
            override suspend fun stations(countryCode: String, query: String, limit: Int) = listOf(station("star-qa", "Radio Etoile QA"))
        }
        try {
            rule.onNodeWithTag("nav-radio").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("radio-country-MA").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("radio-country-MA").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("radio-favorite-star-qa").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("radio-favorite-star-qa").performClick()
            rule.waitUntil(10_000) { runBlocking { app.repository.dao.channels().first() }.any { it.tvgId == "star-qa" && it.favorite } }
            rule.onNodeWithTag("nav-favorites").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Radio Etoile QA").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            app.radioDirectoryOverride = previous
            runBlocking { app.repository.dao.profileByName("Radio · MA")?.let { app.repository.deleteProfile(it.id) } }
        }
    }

    @Test fun radioGuideShowsOnceThenStaysHidden() {
        app.getSharedPreferences("streamlab_tips", 0).edit().putBoolean("radio", false).commit()
        try {
            rule.onNodeWithTag("nav-radio").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("tip-radio").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tip-radio-ok").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("tip-radio").fetchSemanticsNodes().isEmpty() }
            assertTrue(FirstUseTips.seen(app, FirstUseTips.RADIO))
        } finally {
            app.getSharedPreferences("streamlab_tips", 0).edit().putBoolean("radio", true).commit()
        }
    }

    private fun profileName(id: Long): String = runBlocking { app.repository.dao.profile(id)!!.name }

    private fun station(uuid: String, name: String) = RadioStation(
        uuid = uuid, name = name, countryCode = "MA", streamUrl = "https://radio.example/$uuid.mp3",
        favicon = null, codec = "MP3", bitrate = 128, votes = 1, tags = null,
    )
}
