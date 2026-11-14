package app.cleanroom.streamlab

import app.cleanroom.streamlab.services.ServiceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceBrandCatalogTest {
    @Test fun everyServiceHasAnOfficialAppPackageForItsInstalledIcon() {
        assertEquals(ServiceRegistry.all.map { it.id }.toSet(), ServiceBrandCatalog.packageNames.keys)
        assertTrue(ServiceBrandCatalog.packageNames.values.all { it.contains('.') })
    }

    @Test fun noThirdPartyLogoIsBundled() {
        val drawables = R.drawable::class.java.fields.map { it.name }
        assertTrue(drawables.toString(), drawables.none { it.startsWith("brand_") })
    }
}
