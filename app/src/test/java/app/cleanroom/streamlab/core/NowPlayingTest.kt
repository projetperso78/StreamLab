package app.cleanroom.streamlab.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingTest {
    @Test fun keepsARealSongLine() {
        assertEquals("Daft Punk - One More Time", NowPlaying.songLine("  Daft Punk  -  One More Time ", "FIP"))
    }

    @Test fun dropsNoise() {
        assertNull(NowPlaying.songLine(null, "FIP"))
        assertNull(NowPlaying.songLine("", "FIP"))
        assertNull(NowPlaying.songLine(" - ", "FIP"))
        assertNull(NowPlaying.songLine("0000 | 12", "FIP"))
        assertNull(NowPlaying.songLine("fip", "FIP"))
        assertNull(NowPlaying.songLine("https://ads.example/123", "FIP"))
    }

    @Test fun capsVeryLongLines() {
        assertEquals(120, NowPlaying.songLine("a".repeat(400), "FIP")!!.length)
    }

    @Test fun splitsArtistAndTitle() {
        assertEquals("Daft Punk" to "One More Time", NowPlaying.split("Daft Punk - One More Time"))
        assertEquals(null to "Le journal de 8h", NowPlaying.split("Le journal de 8h"))
    }
}
