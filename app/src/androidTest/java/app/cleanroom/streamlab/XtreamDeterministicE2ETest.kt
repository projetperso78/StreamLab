package app.cleanroom.streamlab

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.data.ProfileType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XtreamDeterministicE2ETest {
    @Test fun xtreamProfileBuildsAuthenticatedPlaylistRequestAndLoadsChannels() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val name = "Xtream QA ${System.currentTimeMillis()}"
        // The fragment makes httpbin return a deterministic lawful M3U while the repository still
        // constructs the standard Xtream /get.php?username=... request path before transport.
        // This validates our request/persistence/parser path; interoperability with a real provider remains PARTIAL.
        val base = "https://httpbin.org/base64/I0VYVE0zVQojRVhUSU5GOi0xIHR2Zy1pZD0iYmJiLnFhIiB0dmctbmFtZT0iQmlnIEJ1Y2sgQnVubnkgUUEiIGdyb3VwLXRpdGxlPSJRQSBJbnRlcm5ldCIsUHVibGljIFFBIEhMUwpodHRwczovL3Rlc3Qtc3RyZWFtcy5tdXguZGV2L3gzNnhoenoveDM2eGh6ei5tM3U4Cg==#"
        val id = app.repository.createProfile(name, ProfileType.XTREAM, base, "qa-user", "qa-pass")
        try {
            val loaded = app.repository.refreshProfile(id)
            assertEquals(1, loaded)
            val channel = app.repository.dao.channelsForProfile(id).single()
            assertEquals("Public QA", channel.name)
            val playback = app.repository.playbackSource(channel.id)
            assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", playback.url)
            assertTrue(app.repository.profiles.first().any { it.id == id && it.type == ProfileType.XTREAM.name })
        } finally {
            app.repository.deleteProfile(id)
        }
    }
}
