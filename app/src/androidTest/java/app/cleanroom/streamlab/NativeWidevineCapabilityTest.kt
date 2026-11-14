package app.cleanroom.streamlab

import android.media.MediaDrm
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NativeWidevineCapabilityTest {
    @Test fun reportNativeWidevineSecurity() {
        val widevine = UUID(-1301668207276963122L, -6645017420763422227L)
        assertTrue("Widevine scheme is not supported", MediaDrm.isCryptoSchemeSupported(widevine))
        MediaDrm(widevine).use { drm ->
            fun prop(name: String): String = runCatching { drm.getPropertyString(name) }.getOrElse { "<${it.javaClass.simpleName}>" }
            val report = linkedMapOf(
                "securityLevel" to prop("securityLevel"),
                "vendor" to prop("vendor"),
                "version" to prop("version"),
                "description" to prop("description"),
                "systemId" to prop("systemId"),
                "maxHdcpLevel" to prop("maxHdcpLevel"),
                "connectedHdcpLevel" to prop("hdcpLevel"),
                "usageReportingSupport" to prop("usageReportingSupport"),
            )
            Log.i(TAG, "NATIVE_WIDEVINE=$report")
        }
    }

    companion object { private const val TAG = "NativeWidevineProbe" }
}
