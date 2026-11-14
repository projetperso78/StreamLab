package app.cleanroom.streamlab.cast

import app.cleanroom.streamlab.data.PlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastPolicyTest {
    @Test fun acceptsDirectPublicHlsWithoutHeaders() {
        val media = CastPolicy.mediaFor(
            PlaybackSource("TV", "https://example.org/live/channel.m3u8?token=abc", emptyMap(), null),
            "TV",
        )
        requireNotNull(media)
        assertEquals("application/x-mpegURL", media.contentType)
        assertTrue(media.live)
    }

    @Test fun rejectsLocalAndAuthenticatedSources() {
        assertNull(CastPolicy.mediaFor(PlaybackSource("Local", "content://media/1", emptyMap(), null), "Vidéos"))
        assertNull(CastPolicy.mediaFor(PlaybackSource("Fixture", "fixture://demo.mp4", emptyMap(), null), "Vidéos"))
        assertNull(CastPolicy.mediaFor(PlaybackSource("Auth", "https://example.org/live.m3u8", mapOf("Authorization" to "secret"), null), "TV"))
    }

    @Test fun keepsSeekableRemoteMp4Buffered() {
        val media = CastPolicy.mediaFor(PlaybackSource("Film", "https://example.org/movie.mp4", emptyMap(), null), "Vidéos")
        requireNotNull(media)
        assertEquals("video/mp4", media.contentType)
        assertFalse(media.live)
    }

    @Test fun radioWithoutExtensionUsesAudioMime() {
        val media = CastPolicy.mediaFor(PlaybackSource("Radio", "https://radio.example.org/stream", emptyMap(), null), "Radio")
        requireNotNull(media)
        assertEquals("audio/mpeg", media.contentType)
        assertTrue(media.live)
    }
}
