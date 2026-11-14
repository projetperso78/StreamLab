package app.cleanroom.streamlab

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.core.SourcePolicy
import app.cleanroom.streamlab.data.ProfileType
import app.cleanroom.streamlab.player.PlaybackService
import app.cleanroom.streamlab.services.WebDavClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home network drive end to end, against the QA WebDAV server the developer runs on the PC
 * (reachable from the emulator at 10.0.2.2:8099). Skipped when that server is not running.
 */
@RunWith(AndroidJUnit4::class)
class NetworkShareTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as StreamLabApplication
    private val share = "http://10.0.2.2:8099/video"

    private fun serverUp() = runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress("10.0.2.2", 8099), 1500) } }.isSuccess

    @Test(timeout = 90_000)
    fun browsesTheDriveAndPlaysAFilm() {
        assumeTrue("QA WebDAV server not running on the PC", serverUp())
        val profile = runBlocking { app.repository.createProfile("NAS QA", ProfileType.WEBDAV, share, "qa", "qa-pass") }
        val controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync().get(15, TimeUnit.SECONDS)
        try {
            val client = WebDavClient(app.network.playerClient())
            val entries = client.list(share, "qa", "qa-pass")
            assertEquals(listOf("Demo QA.mp4"), entries.map { it.name })
            val id = runBlocking { app.repository.addShareVideo(profile, entries[0].name, entries[0].url, client.authHeaders("qa", "qa-pass")) }
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.Builder().setMediaId(OpaqueMediaId.channel(id)).build())
                controller.prepare(); controller.play()
            }
            val end = System.currentTimeMillis() + 30_000
            var playing = false
            while (!playing && System.currentTimeMillis() < end) {
                instrumentation.runOnMainSync { playing = controller.isPlaying }
                if (!playing) Thread.sleep(300)
            }
            assertEquals(true, playing)
            // Wrong password: refused clearly, nothing listed.
            assertThrows(IllegalArgumentException::class.java) { client.list(share, "qa", "wrong") }
        } finally {
            instrumentation.runOnMainSync { controller.stop(); controller.clearMediaItems(); controller.release() }
            runBlocking { app.repository.deleteProfile(profile) }
        }
    }

    @Test fun anotherLocalAddressStaysBlocked() {
        runBlocking { app.repository.createProfile("NAS QA 2", ProfileType.WEBDAV, share, "qa", "qa-pass") }.let { id ->
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    SourcePolicy.requireMediaRemote("http://10.0.2.2:80/router-admin")
                }
            } finally { runBlocking { app.repository.deleteProfile(id) } }
        }
    }
}
