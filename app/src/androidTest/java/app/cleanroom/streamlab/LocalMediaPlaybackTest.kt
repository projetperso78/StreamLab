package app.cleanroom.streamlab

import android.content.ComponentName
import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.data.ProfileType
import app.cleanroom.streamlab.player.PlaybackService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalMediaPlaybackTest {
    @Test fun localMp4FromMediaLibraryPlaysThroughStreamLabService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as StreamLabApplication
        val resolver = context.contentResolver
        val name = "streamlab-local-media-${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/StreamLabQA")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
        var profileId = 0L
        var controller: MediaController? = null
        try {
            resolver.openOutputStream(uri)?.use { output ->
                context.assets.open("demo.mp4").use { input -> input.copyTo(output) }
            } ?: error("Cannot write local media fixture")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)

            val channelId = runBlocking {
                profileId = app.repository.createProfile("Local QA Movie", ProfileType.LOCAL_MEDIA, uri.toString())
                val profile = requireNotNull(app.repository.dao.profile(profileId))
                assertEquals(ProfileType.LOCAL_MEDIA.name, profile.type)
                val rows = app.repository.dao.channelsForProfile(profileId)
                assertEquals(1, rows.size)
                val source = app.repository.playbackSource(rows.single().id)
                assertEquals(uri.toString(), source.url)
                rows.single().id
            }
            val source = runBlocking { app.repository.playbackSource(channelId) }

            controller = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java)),
            ).setApplicationLooper(Looper.getMainLooper()).buildAsync().get(10, TimeUnit.SECONDS)

            val ready = CountDownLatch(1)
            val playbackError = AtomicReference<PlaybackException?>()
            val videoSeen = AtomicBoolean(false)
            instrumentation.runOnMainSync {
                controller!!.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError.set(error)
                        ready.countDown()
                    }
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        if (tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }) videoSeen.set(true)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            if (controller!!.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }) videoSeen.set(true)
                            ready.countDown()
                        }
                    }
                })
            }

            val extras = Bundle().apply {
                putString("media_id", OpaqueMediaId.channel(channelId))
                putString("url", source.url)
                putString("title", source.name)
                putBundle("headers", Bundle.EMPTY)
            }
            val commandFuture = AtomicReference<com.google.common.util.concurrent.ListenableFuture<SessionResult>>()
            instrumentation.runOnMainSync {
                commandFuture.set(
                    controller!!.sendCustomCommand(
                        SessionCommand(PlaybackService.PLAY_SOURCE, Bundle.EMPTY),
                        extras,
                    )
                )
            }
            val commandResult = commandFuture.get().get(10, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, commandResult.resultCode)
            assertTrue("Local video never reached READY", ready.await(20, TimeUnit.SECONDS))
            assertNull(playbackError.get())
            assertTrue("Local file has no video track", videoSeen.get())
        } finally {
            controller?.let { value -> instrumentation.runOnMainSync { value.release() } }
            if (profileId != 0L) runBlocking { app.repository.deleteProfile(profileId) }
            resolver.delete(uri, null, null)
        }
    }
}
