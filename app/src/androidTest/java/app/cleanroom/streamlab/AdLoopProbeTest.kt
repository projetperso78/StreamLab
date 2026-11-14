package app.cleanroom.streamlab

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.player.PlaybackService
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Manual diagnostic (real network): what a preroll-ad radio does over 80 s. Run on demand only. */
@RunWith(AndroidJUnit4::class)
class AdLoopProbeTest {
    @Test(timeout = 150_000)
    fun logWhatAnAdRadioDoes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("run with -e adprobe 1", InstrumentationRegistry.getArguments().getString("adprobe") != null)
        val app = instrumentation.targetContext.applicationContext as StreamLabApplication
        val url = InstrumentationRegistry.getArguments().getString("url")
            ?: "https://streaming.nrjaudio.fm/ou7xugf3s5gi?origine=playernrj&aw_0_req.userConsentV2=&aw_0_1st.station=NRJ-Hits"
        lateinit var controller: MediaController
        instrumentation.runOnMainSync {}
        controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync().get(15, TimeUnit.SECONDS)
        val start = System.currentTimeMillis()
        fun log(text: String) { android.util.Log.i("AdLoopProbe", "+${(System.currentTimeMillis() - start) / 1000}s $text") }
        var request: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult>? = null
        instrumentation.runOnMainSync {
            controller.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { log("state=$state") }
                override fun onPlayerError(error: PlaybackException) { log("error=${error.errorCodeName}") }
                override fun onMediaMetadataChanged(m: androidx.media3.common.MediaMetadata) { log("meta title=${m.title} artist=${m.artist}") }
                override fun onPositionDiscontinuity(o: Player.PositionInfo, n: Player.PositionInfo, reason: Int) { log("discontinuity reason=$reason pos=${n.positionMs}") }
            })
            request = controller.sendCustomCommand(SessionCommand(PlaybackService.PLAY_SOURCE, Bundle.EMPTY), Bundle().apply {
                putString("media_id", OpaqueMediaId.channel(999_999))
                putString("url", url)
                putString("title", "Probe")
            })
        }
        log("play result=${request!!.get(15, TimeUnit.SECONDS).resultCode}")
        repeat(16) {
            Thread.sleep(5_000)
            instrumentation.runOnMainSync { log("t pos=${controller.currentPosition} playing=${controller.isPlaying}") }
        }
        instrumentation.runOnMainSync { controller.stop(); controller.release() }
    }
}
