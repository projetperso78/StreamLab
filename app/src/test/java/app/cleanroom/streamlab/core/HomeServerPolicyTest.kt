package app.cleanroom.streamlab.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Local addresses stay blocked except the exact home server the user added. */
class HomeServerPolicyTest {
    @After fun reset() { SourcePolicy.homeOrigins = emptySet() }

    @Test fun localAddressesAreBlockedByDefault() {
        assertThrows(IllegalArgumentException::class.java) { SourcePolicy.requireMediaRemote("http://192.168.1.10:5005/film.mkv") }
    }

    @Test fun theAddedServerIsAllowed() {
        SourcePolicy.homeOrigins = setOf(SourcePolicy.origin("http://192.168.1.10:5005/video")!!)
        assertEquals("http://192.168.1.10:5005/video/film.mkv", SourcePolicy.requireMediaRemote("http://192.168.1.10:5005/video/film.mkv"))
    }

    @Test fun onlyThatExactHostAndPort() {
        SourcePolicy.homeOrigins = setOf(SourcePolicy.origin("http://192.168.1.10:5005/")!!)
        assertThrows(IllegalArgumentException::class.java) { SourcePolicy.requireMediaRemote("http://192.168.1.10:80/admin") }
        assertThrows(IllegalArgumentException::class.java) { SourcePolicy.requireMediaRemote("http://192.168.1.1:5005/") }
        assertThrows(IllegalArgumentException::class.java) { SourcePolicy.requireMediaRemote("http://localhost:5005/") }
    }

    @Test fun defaultPortsAreNormalised() {
        assertEquals("https://nas.home:443", SourcePolicy.origin("https://NAS.home/video"))
        assertEquals("http://10.0.0.5:80", SourcePolicy.origin("http://10.0.0.5/x"))
    }
}
