package app.cleanroom.streamlab.core

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidePayloadTest {
    private val xml = """<?xml version="1.0"?><tv><channel id="demo"><display-name>Demo</display-name></channel><programme start="20261003200000 +0000" stop="20261003210000 +0000" channel="demo"><title>Now</title></programme></tv>"""

    @Test fun plainXmlIsPassedThrough() {
        val guide = GuidePayload.open(xml.toByteArray(), "https://example.org/guide.xml").use { XmlTvParser().parse(it) }
        assertEquals("Demo", guide.channelNames["demo"])
        assertEquals("Now", guide.programmes.single().title)
    }

    @Test fun gzipXmlIsDecodedByMagicOrExtension() {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(xml.toByteArray()) }
        val guide = GuidePayload.open(output.toByteArray(), "https://example.org/guide.xml.gz").use { XmlTvParser().parse(it) }
        assertEquals("Demo", guide.channelNames["demo"])
        assertEquals(1, guide.programmes.size)
    }
}
