package app.cleanroom.streamlab.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Names taken from the real French directory on 13 Nov 2026. */
class StationTidyTest {
    @Test fun dropsTechnicalNoise() {
        assertEquals("BFM Radio", StationTidy.cleanName("BFM Radio (128.mp3)"))
        assertEquals("Le Bon Mix", StationTidy.cleanName("Le Bon Mix HiFi Flac 1411 Kbps"))
        assertEquals("france info", StationTidy.cleanName("france info (lofi)"))
        assertEquals("Radio Notre Dame", StationTidy.cleanName("Radio Notre Dame (lower bitrate)"))
    }

    @Test fun dropsEmojiAndWebAddresses() {
        assertEquals("RFI Monde", StationTidy.cleanName("📺 RFI Monde"))
        assertEquals("LOVE RADIO Only Love Songs 70s80s90s", StationTidy.cleanName("LOVE RADIO Only Love Songs 70s80s90s - www.love.radio"))
    }

    @Test fun keepsMeaningfulParts() {
        assertEquals("Kiss FM (de Toulon à Marseille)", StationTidy.cleanName("Kiss FM (de Toulon à Marseille)"))
        assertEquals("France Musique · Classique Easy", StationTidy.cleanName("France Musique | Classique Easy"))
        assertEquals("Jazzradio.fr Jazz & Classique", StationTidy.cleanName("Jazzradio.fr Jazz & Classique"))
        assertEquals("Autoroute Info 107.7 Zone Nord", StationTidy.cleanName("Autoroute Info 107.7 Zone Nord"))
    }

    @Test fun softensShoutingButKeepsAcronyms() {
        assertEquals("Radio Francia Internacional", StationTidy.cleanName("RADIO FRANCIA INTERNACIONAL"))
        assertEquals("RTL", StationTidy.cleanName("RTL"))
    }

    @Test fun neverBlank() {
        assertEquals("(128.mp3)", StationTidy.cleanName("(128.mp3)"))
    }

    @Test fun genresSkipCodecsAndOwners() {
        assertEquals("Information", StationTidy.genres("aac,information,public radio,radio france"))
        assertEquals("Classical · Jazz", StationTidy.genres("classical,jazz,music,public radio,radio france"))
        assertEquals("", StationTidy.genres(null))
    }

    @Test fun duplicatesShareAKey() {
        assertEquals(StationTidy.duplicateKey("France Info"), StationTidy.duplicateKey("france info (lofi)"))
    }

    @Test fun localLanguageFirst() {
        val fr = StationTidy.localLanguages("FR")
        assertTrue("french" in fr)
        assertTrue(StationTidy.isLocal("french", fr))
        assertTrue(StationTidy.isLocal("", fr))
        assertTrue(StationTidy.isLocal("english,french", fr))
        assertFalse(StationTidy.isLocal("persian", fr))
    }
}
