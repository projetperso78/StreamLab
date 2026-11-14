package app.cleanroom.streamlab.core

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class M3uParserTest {
    @Test fun metadataHeadersAndDuplicates() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="id-1" tvg-name="World" tvg-logo="https://example.org/logo.png" group-title="News",World One
            #EXTVLCOPT:http-user-agent=FixtureAgent
            https://example.org/live.m3u8
            https://example.org/live.m3u8
        """.trimIndent()
        val channels = M3uParser().parse(StringReader(text))
        assertEquals(1, channels.size)
        assertEquals("World One", channels[0].name)
        assertEquals("id-1", channels[0].tvgId)
        assertEquals("News", channels[0].group)
        assertEquals("FixtureAgent", channels[0].headers["User-Agent"])
    }
    @Test fun malformedLinesAreIgnoredAndPublicHttpMediaIsAccepted() {
        val text = "#EXTINF:broken,Good\nnot a URL\n# comment\nhttp://example.org/a\nhttps://example.org/b"
        val channels = M3uParser().parse(StringReader(text))
        assertEquals(2, channels.size)
        assertTrue(channels.any { it.url == "http://example.org/a" })
        assertTrue(channels.any { it.url == "https://example.org/b" })
    }
    @Test(expected = IllegalArgumentException::class)
    fun oversizedLineRejected() { M3uParser(maxChars = 100).parse(StringReader("x".repeat(101))) }
    @Test fun fuzzLikeMalformedInputIsBounded() {
        val random = java.util.Random(17)
        repeat(100) {
            val text = buildString {
                append("#EXTM3U\n")
                repeat(50) {
                    repeat(random.nextInt(40)) { append((32 + random.nextInt(90)).toChar()) }
                    append('\n')
                }
            }
            assertTrue(M3uParser(maxChars = 5000, maxChannels = 100).parse(StringReader(text)).size <= 100)
        }
    }
}
