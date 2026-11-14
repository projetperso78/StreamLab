package app.cleanroom.streamlab.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCodeTest {
    @Test fun roundTripsRadiosAndPlaylists() {
        val bundle = ShareCode.Bundle(
            radios = listOf(ShareCode.Radio("Radio Test", "https://stream.example.org/live.mp3", "fr")),
            playlists = listOf(ShareCode.Playlist("Ma liste", "https://example.org/list.m3u")),
        )
        val code = ShareCode.encode(bundle)
        assertTrue(code.startsWith(ShareCode.PREFIX))
        val decoded = ShareCode.decode(code)
        assertEquals("FR", decoded.radios.single().country)
        assertEquals("https://stream.example.org/live.mp3", decoded.radios.single().url)
        assertEquals("Ma liste", decoded.playlists.single().name)
    }

    @Test fun neverSharesAccountLinks() {
        val bundle = ShareCode.Bundle(
            radios = listOf(ShareCode.Radio("Ok", "https://stream.example.org/a.mp3", null)),
            playlists = listOf(
                ShareCode.Playlist("Xtream", "https://provider.example/get.php?username=me&password=secret&type=m3u"),
                ShareCode.Playlist("Token", "https://example.org/list.m3u?token=abc"),
            ),
        )
        val decoded = ShareCode.decode(ShareCode.encode(bundle))
        assertTrue(decoded.playlists.isEmpty())
        assertEquals(1, decoded.radios.size)
    }

    @Test fun rejectsForeignOrOversizedCodes() {
        assertThrows(IllegalArgumentException::class.java) { ShareCode.decode("https://evil.example") }
        assertThrows(IllegalArgumentException::class.java) { ShareCode.decode(ShareCode.PREFIX + "A".repeat(5000)) }
    }

    @Test fun refusesEmptyShare() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCode.encode(ShareCode.Bundle(emptyList(), listOf(ShareCode.Playlist("x", "file:///sdcard/a.m3u"))))
        }
    }

    @Test fun twentyFiveRadiosStillFitInOneQrCode() {
        val radios = (1..40).map { ShareCode.Radio("Station numéro $it", "https://stream$it.example.org/live/high.mp3", "MA") }
        val code = ShareCode.encode(ShareCode.Bundle(radios, emptyList()))
        assertTrue("code length ${code.length}", code.length <= 2_500)
        assertEquals(25, ShareCode.decode(code).radios.size)
    }
}
