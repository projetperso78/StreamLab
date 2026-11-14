package app.cleanroom.streamlab.services

import org.junit.Assert.*
import org.junit.Test

class ServerAddressPolicyTest {
    @Test fun allowsHttpsAndExplicitPrivateServersWithPorts() {
        for (url in listOf("https://demo.jellyfin.org/stable/", "http://192.168.1.20:8096", "http://nas.local:8096/jellyfin", "https://media.example:8920")) {
            assertEquals(url, ServerAddressPolicy.validate(url))
        }
    }
    @Test fun rejectsCredentialsAndPublicCleartext() {
        for (url in listOf("http://public.example", "https://user:password@server.example", "javascript:alert(1)", "file:///tmp/movie", "https://server.example?token=secret", "http://localhost:8096")) {
            assertThrows(IllegalArgumentException::class.java) { ServerAddressPolicy.validate(url) }
        }
    }
    @Test fun localNavigationCannotEscapeConfiguredOrigin() {
        val home = "http://192.168.1.20:8096"
        ServerAddressPolicy.requireNavigation("$home/web/index.html#!/login", home)
        for (target in listOf("http://192.168.1.21:8096/web", "http://192.168.1.20:8080/web", "http://user:pass@192.168.1.20:8096/web")) {
            assertThrows(IllegalArgumentException::class.java) { ServerAddressPolicy.requireNavigation(target, home) }
        }
    }
}
