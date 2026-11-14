package app.cleanroom.streamlab.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multistatus answer in the shape Synology/Nextcloud send. */
class WebDavParseTest {
    private val xml = """<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
 <d:response><d:href>/video/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
 <d:response><d:href>/video/Films/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
 <d:response><d:href>/video/Le%20Grand%20Voyage.mkv</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>1500000000</d:getcontentlength></d:prop></d:propstat></d:response>
 <d:response><d:href>/video/notes.txt</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>12</d:getcontentlength></d:prop></d:propstat></d:response>
</d:multistatus>"""

    @Test fun foldersThenVideosWithoutTheFolderItself() {
        val entries = WebDavClient.parse(xml, "http://192.168.1.10:5005/video/")
        assertEquals(listOf("Films", "Le Grand Voyage.mkv"), entries.map { it.name })
        assertTrue(entries[0].folder)
        assertFalse(entries[1].folder)
        assertEquals("http://192.168.1.10:5005/video/Le%20Grand%20Voyage.mkv", entries[1].url)
        assertEquals(1_500_000_000L, entries[1].size)
    }

    @Test fun onlyPlayableFilesAreListed() {
        assertTrue(WebDavClient.isPlayable("film.MP4"))
        assertFalse(WebDavClient.isPlayable("notes.txt"))
    }

    @Test fun answersWithEntitiesAreRefused() {
        val hostile = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/hosts\">]><d:multistatus xmlns:d=\"DAV:\"/>"
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { WebDavClient.parse(hostile, "http://nas/") }
    }
}
