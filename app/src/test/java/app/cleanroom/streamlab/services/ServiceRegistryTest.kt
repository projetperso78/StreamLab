package app.cleanroom.streamlab.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRegistryTest {
    @Test fun registryContainsUniqueOfficialHttpsTargets() {
        val services = ServiceRegistry.all
        assertEquals(listOf("YouTube", "Netflix", "Prime Video", "Disney+", "Twitch", "Plex", "Jellyfin", "Emby"), services.map { it.name })
        assertEquals(services.size, services.map { it.id }.toSet().size)
        services.forEach {
            assertEquals("https", it.website.scheme)
            assertFalse(it.website.host.isNullOrBlank())
            assertTrue(it.website.userInfo == null)
        }
    }

    @Test fun registryContainsOnlyServicesWithAnOfficialApp() {
        assertEquals(ServiceRegistry.all.map { it.id }.toSet(), ServiceRegistry.officialPackages.keys)
        assertTrue(ServiceRegistry.all.none { it.id.startsWith("fr_") })
    }

    @Test fun rejectsUnsafeWebsites() {
        listOf("http://example.com", "file:///tmp/x", "javascript:alert(1)",
            "https://user:pass@example.com", "https://example.com:444/").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { ServiceRegistry.validatedWebsite(url) }
        }
    }
}

