package app.cleanroom.streamlab.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class XmlTvParserTest {
    @Test fun timezoneAndChannelNames() {
        val xml = """<tv><channel id="news"><display-name>News One</display-name></channel><programme channel="news" start="20260101120000 +0200" stop="20260101130000 +0200"><title>Midday</title></programme></tv>"""
        val guide = XmlTvParser().parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals("News One", guide.channelNames["news"])
        assertEquals("Midday", guide.programmes.single().title)
        assertEquals(1767261600000L, guide.programmes.single().start)
    }
    @Test fun multilingualTitleProducesOneProgramme() {
        val xml = """<tv><programme channel="one" start="20260101120000 +0000" stop="20260101130000 +0000"><title lang="en">One</title><title lang="fr">Un</title></programme></tv>"""
        assertEquals(1, XmlTvParser().parse(ByteArrayInputStream(xml.toByteArray())).programmes.size)
    }
    @Test(expected = Exception::class)
    fun dtdBlocked() {
        val xml = """<!DOCTYPE tv [<!ENTITY x SYSTEM "file:///etc/passwd">]><tv><channel id="x"><display-name>&x;</display-name></channel></tv>"""
        XmlTvParser().parse(ByteArrayInputStream(xml.toByteArray()))
    }
    @Test(expected = Exception::class)
    fun inputBounded() { XmlTvParser(maxBytes = 10).parse(ByteArrayInputStream("<tv></tv>".repeat(10).toByteArray())) }
}
