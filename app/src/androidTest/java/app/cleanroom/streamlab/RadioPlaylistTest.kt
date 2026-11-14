package app.cleanroom.streamlab

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.data.CatalogChannelRow
import app.cleanroom.streamlab.player.PlaybackService
import app.cleanroom.streamlab.services.RadioCountry
import app.cleanroom.streamlab.services.RadioDirectoryGateway
import app.cleanroom.streamlab.services.RadioStation
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Favourite radios in rotation: a dead station hands over to the next one, Next moves on. */
@RunWith(AndroidJUnit4::class)
class RadioPlaylistTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as StreamLabApplication

    @Test(timeout = 120_000)
    fun deadStationHandsOverAndNextMovesOn() {
        val ids = runBlocking {
            listOf(
                app.repository.addRadioStation("FR", "Playlist morte QA", "https://dead.streamlab-qa.invalid/a.mp3", null, "pl-dead"),
                app.repository.addRadioStation("FR", "Playlist deux QA", PublicTestStream.URL, null, "pl-two"),
                app.repository.addRadioStation("FR", "Playlist trois QA", PublicTestStream.URL + "?third", null, "pl-three"),
            )
        }
        val controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .buildAsync().get(15, TimeUnit.SECONDS)
        fun <T> onMain(block: () -> T): T { var r: T? = null; instrumentation.runOnMainSync { r = block() }; @Suppress("UNCHECKED_CAST") return r as T }
        fun waitFor(ms: Long, check: () -> Boolean) {
            val end = System.currentTimeMillis() + ms
            while (!check()) { require(System.currentTimeMillis() < end) { "timed out" }; Thread.sleep(300) }
        }
        try {
            onMain {
                controller.setMediaItems(ids.map { MediaItem.Builder().setMediaId(OpaqueMediaId.channel(it)).build() })
                controller.repeatMode = Player.REPEAT_MODE_ALL
                controller.prepare(); controller.play()
            }
            // The first station cannot resolve: the second one plays by itself.
            waitFor(60_000) { onMain { controller.isPlaying && controller.currentMediaItem?.mediaId == OpaqueMediaId.channel(ids[1]) } }
            onMain { controller.seekToNext() }
            waitFor(20_000) { onMain { controller.currentMediaItem?.mediaId == OpaqueMediaId.channel(ids[2]) } }
            assertEquals(OpaqueMediaId.channel(ids[2]), onMain { controller.currentMediaItem?.mediaId })
        } finally {
            onMain { controller.repeatMode = Player.REPEAT_MODE_OFF; controller.stop(); controller.clearMediaItems(); controller.release() }
            runBlocking { app.repository.dao.profileByName("Radio · FR")?.let { app.repository.deleteProfile(it.id) } }
        }
    }

    @Test(timeout = 90_000)
    fun voiceRequestPlaysAStationNeverSavedBefore() {
        val previous = app.radioDirectoryOverride
        app.radioDirectoryOverride = object : RadioDirectoryGateway {
            override suspend fun countries() = emptyList<RadioCountry>()
            override suspend fun stations(countryCode: String, query: String, limit: Int) = listOf(
                RadioStation("voice-other", "Radio Etoile Plus", countryCode, PublicTestStream.URL + "?b", null, null, 0, 0, null),
                RadioStation("voice-qa", "Radio Etoile", countryCode, PublicTestStream.URL, null, null, 0, 0, null),
            )
        }
        val controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .buildAsync().get(15, TimeUnit.SECONDS)
        fun <T> onMain(block: () -> T): T { var r: T? = null; instrumentation.runOnMainSync { r = block() }; @Suppress("UNCHECKED_CAST") return r as T }
        try {
            onMain {
                controller.setMediaItem(MediaItem.Builder().setRequestMetadata(
                    MediaItem.RequestMetadata.Builder().setSearchQuery("mets la radio Radio Etoile sur StreamLab").build()).build())
                controller.prepare(); controller.play()
            }
            val end = System.currentTimeMillis() + 40_000
            var saved: CatalogChannelRow? = null
            while (saved == null && System.currentTimeMillis() < end) {
                saved = runBlocking { app.repository.dao.channels().first() }.firstOrNull { it.tvgId == "voice-qa" }
                if (saved == null) Thread.sleep(300)
            }
            requireNotNull(saved) { "the exact station was not picked" }
            val target = OpaqueMediaId.channel(saved.id)
            while (onMain { controller.currentMediaItem?.mediaId } != target) {
                require(System.currentTimeMillis() < end) { "not playing the requested station" }
                Thread.sleep(300)
            }
        } finally {
            app.radioDirectoryOverride = previous
            onMain { controller.stop(); controller.clearMediaItems(); controller.release() }
            runBlocking { app.repository.dao.channels().first().filter { it.tvgId == "voice-qa" || it.tvgId == "voice-other" }
                .map { it.profileId }.distinct().forEach { app.repository.deleteProfile(it) } }
        }
    }
}
