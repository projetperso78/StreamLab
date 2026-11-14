package app.cleanroom.streamlab

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Live network checks, deliberately separate from the deterministic regression suite. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class LivePlaybackValidationTest {
    @Test fun frenchRadio() = radio("FR")
    @Test fun britishRadio() = radio("GB")

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication

    private fun radio(country: String) {
        val stations = runBlocking { app.radioDirectory.stations(country, limit = 12) }
        assertTrue("Country filtering failed", stations.isNotEmpty() && stations.all { it.countryCode == country })
        var success = false
        // Public stations may be temporarily unavailable; log every attempt, never hide a failure.
        for (station in stations.take(3)) {
            if (play("$country/${station.name}", station.streamUrl, false)) { success = true; break }
        }
        assertTrue("No playable station among first three for $country", success)
    }

    private fun play(name: String, url: String, video: Boolean): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: ExoPlayer
        val scenario = if (video) ActivityScenario.launch(MainActivity::class.java) else null
        var error = ""
        var firstFrame = false
        var playingSinceNanos: Long? = null
        var audioAtReady = 0
        var videoAtReady = 0
        var success = false
        var position = 0L
        var audioBuffers = 0
        var videoBuffers = 0
        var phase = "starting"
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(app).setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(app.network.playerClient()))).build()
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { firstFrame = true }
                override fun onPlayerError(value: PlaybackException) { error = value.errorCodeName }
            })
            player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.play()
        }
        scenario?.onActivity { activity ->
            val activePlayer = player
            activity.setContentView(PlayerView(activity).apply { this.player = activePlayer })
        }
        try {
            val deadline = System.nanoTime() + 30_000_000_000L
            while (System.nanoTime() < deadline && !success && error.isEmpty()) {
                Thread.sleep(400)
                instrumentation.runOnMainSync {
                    position = player.currentPosition
                    audioBuffers = player.audioDecoderCounters?.renderedOutputBufferCount ?: 0
                    videoBuffers = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
                    if (player.isPlaying && player.playbackState == Player.STATE_READY) {
                        if (playingSinceNanos == null) {
                            playingSinceNanos = System.nanoTime()
                            audioAtReady = audioBuffers
                            videoAtReady = videoBuffers
                        }
                        success = System.nanoTime() - playingSinceNanos!! >= 2_000_000_000L &&
                            audioBuffers > audioAtReady && (!video || firstFrame && videoBuffers > videoAtReady)
                    } else {
                        playingSinceNanos = null
                    }
                }
            }
            if (success) {
                phase = "pause"
                var audioAtPause = 0
                var videoAtPause = 0
                instrumentation.runOnMainSync {
                    player.pause()
                    audioAtPause = player.audioDecoderCounters?.renderedOutputBufferCount ?: 0
                    videoAtPause = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
                }
                Thread.sleep(700)
                var pauseVerified = false
                instrumentation.runOnMainSync {
                    // The HLS live-window origin may move while paused. Verify player state, not absolute clock.
                    pauseVerified = !player.isPlaying && !player.playWhenReady
                    player.play()
                }
                var resumed = false
                var audioAfter = audioAtPause
                var videoAfter = videoAtPause
                val resumeDeadline = System.nanoTime() + 8_000_000_000L
                while (!resumed && System.nanoTime() < resumeDeadline && error.isEmpty()) {
                    Thread.sleep(400)
                    instrumentation.runOnMainSync {
                        audioAfter = player.audioDecoderCounters?.renderedOutputBufferCount ?: 0
                        videoAfter = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
                        resumed = player.isPlaying && player.playbackState == Player.STATE_READY &&
                            audioAfter > audioAtPause && (!video || videoAfter > videoAtPause)
                    }
                }
                success = pauseVerified && resumed
                if (success) phase = "complete" else if (!pauseVerified) phase = "pause" else phase = "resume"
                println("STREAMLAB_RESUME|" + name + "|pauseVerified=" + pauseVerified +
                    "|resumed=" + resumed + "|audioAtPause=" + audioAtPause +
                    "|videoAtPause=" + videoAtPause + "|audioNow=" + audioAfter +
                    "|videoNow=" + videoAfter)
            }
            println("STREAMLAB_LIVE|$name|pass=$success|positionMs=$position|audioBuffers=$audioBuffers|videoBuffers=$videoBuffers|firstFrame=$firstFrame|pauseResume=$success|phase=$phase|error=$error")
            return success
        } finally {
            instrumentation.runOnMainSync { player.release() }
            scenario?.close()
        }
    }
}
