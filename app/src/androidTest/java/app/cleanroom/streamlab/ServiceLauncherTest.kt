package app.cleanroom.streamlab

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.services.ActivityLaunchTarget
import app.cleanroom.streamlab.services.ExternalService
import app.cleanroom.streamlab.services.LaunchResult
import app.cleanroom.streamlab.services.ServerEndpoints
import app.cleanroom.streamlab.services.ServiceLauncher
import app.cleanroom.streamlab.services.ServiceRegistry
import java.io.File
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceLauncherTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun forgetPersonalServers() {
        ServerEndpoints.CONFIGURABLE.forEach { File(context.filesDir, "server-$it.txt").delete() }
    }

    @Test fun installedOfficialAppsLaunchDirectly() {
        ServiceRegistry.all.forEach { service ->
            val captured = mutableListOf<Intent>()
            val launcher = ServiceLauncher(context, nativeIntentResolver = { Intent().setPackage(it) }) { intent, _ -> captured += intent }
            assertEquals(service.id, LaunchResult.NATIVE, launcher.launch(service))
            assertEquals(service.id, ServiceRegistry.officialPackages.getValue(service.id), captured.single().`package`)
        }
    }

    @Test fun missingOfficialAppOpensItsPlayStoreListing() {
        ServiceRegistry.all.filterNot { it.id in ServerEndpoints.CONFIGURABLE }.forEach { service ->
            val captured = mutableListOf<Intent>()
            val result = ServiceLauncher(context, nativeIntentResolver = { null }) { intent, _ -> captured += intent }.launch(service)
            assertEquals(service.id, LaunchResult.STORE, result)
            val intent = captured.single()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("market://details?id=${ServiceRegistry.officialPackages.getValue(service.id)}", intent.dataString)
            assertTrue("StreamLab must never host the service itself", intent.component?.packageName != context.packageName)
        }
    }

    @Test fun personalServersOpenConfigurationWhenNotYetSaved() {
        for (id in ServerEndpoints.CONFIGURABLE) {
            val captured = mutableListOf<Intent>()
            val result = ServiceLauncher(context, nativeIntentResolver = { null }) { intent, _ -> captured += intent }
                .launch(ServiceRegistry.all.first { it.id == id })
            assertEquals(LaunchResult.SETUP, result)
            assertEquals(ServerSetupActivity::class.java.name, captured.single().component?.className)
        }
    }

    @Test fun savedPersonalServerOpensInTheSystemBrowser() {
        ServerEndpoints(context).save("jellyfin", "https://demo.jellyfin.org/stable/")
        val captured = mutableListOf<Intent>()
        val result = ServiceLauncher(context, nativeIntentResolver = { null }) { intent, _ -> captured += intent }
            .launch(ServiceRegistry.all.first { it.id == "jellyfin" })
        assertEquals(LaunchResult.EXTERNAL_BROWSER, result)
        assertEquals("https://demo.jellyfin.org/stable/", captured.single().dataString)
        assertTrue(captured.single().component?.packageName != context.packageName)
    }

    @Test fun externalAppHandoffTargetsTheCurrentDisplay() {
        val youtube = ServiceRegistry.all.first { it.id == "youtube" }
        val captured = mutableListOf<Pair<Intent, Int?>>()
        val launcher = ServiceLauncher(
            context = context,
            nativeIntentResolver = { Intent().setPackage(it) },
            launchDisplayIdProvider = { 7 },
            activityStarter = { intent, displayId -> captured += intent to displayId },
        )
        assertEquals(LaunchResult.NATIVE, launcher.launch(youtube))
        assertEquals(7, captured.single().second)
        assertEquals(7, ActivityLaunchTarget.optionsForDisplay(captured.single().second)?.launchDisplayId)
        assertNull(ActivityLaunchTarget.optionsForDisplay(null))
        assertNull(ActivityLaunchTarget.displayIdFor(context))
    }

    @Test fun unregisteredOrUnsafeWebsiteCannotLaunch() {
        val captured = mutableListOf<Intent>()
        val launcher = ServiceLauncher(context, nativeIntentResolver = { null }) { intent, _ -> captured += intent }
        assertThrows(IllegalArgumentException::class.java) {
            launcher.launch(ExternalService("bad", "Bad", URI("http://example.org/")))
        }
        assertTrue(captured.isEmpty())
    }
}
