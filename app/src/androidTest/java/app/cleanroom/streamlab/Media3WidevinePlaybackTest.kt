package app.cleanroom.streamlab

import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3WidevinePlaybackTest {
    @Test fun samePublicWidevineAssetPlaysThroughNativeMedia3() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        val latch = CountDownLatch(1)
        val result = AtomicReference("no-result")
        lateinit var player: ExoPlayer
        lateinit var view: PlayerView

        instrumentation.runOnMainSync {
            view = PlayerView(activity)
            activity.addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            player = ExoPlayer.Builder(activity).build()
            view.player = player
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    result.set("errorCode=${error.errorCode} name=${error.errorCodeName} message=${error.message} cause=${error.cause}")
                    latch.countDown()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        result.set("playing position=${player.currentPosition} video=${player.videoFormat} audio=${player.audioFormat}")
                    }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        result.set("ready position=${player.currentPosition} video=${player.videoFormat} audio=${player.audioFormat}")
                    }
                }
            })
            val drm = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(LICENSE_URL)
                .setForceDefaultLicenseUri(true)
                .build()
            val item = MediaItem.Builder()
                .setUri(MANIFEST_URL)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .setDrmConfiguration(drm)
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
        }

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
        while (System.nanoTime() < deadline && latch.count > 0) {
            Thread.sleep(250)
            var pos = 0L
            var formats = ""
            instrumentation.runOnMainSync {
                pos = player.currentPosition
                formats = "video=${player.videoFormat} audio=${player.audioFormat}"
            }
            if (pos > 500) {
                result.set("played position=$pos $formats")
                latch.countDown()
            }
        }
        Log.i(TAG, "MEDIA3_WIDEVINE=${result.get()}")
        assertTrue("Native Media3 Widevine did not reach playback: ${result.get()}", latch.count == 0L && !result.get().startsWith("errorCode="))

        instrumentation.runOnMainSync {
            view.player = null
            player.release()
            (view.parent as? ViewGroup)?.removeView(view)
            activity.finish()
        }
    }

    companion object {
        private const val TAG = "Media3WidevineProbe"
        private const val MANIFEST_URL = "https://storage.googleapis.com/shaka-demo-assets/angel-one-widevine/dash.mpd"
        private const val LICENSE_URL = "https://cwip-shaka-proxy.appspot.com/no_auth"
    }
}
