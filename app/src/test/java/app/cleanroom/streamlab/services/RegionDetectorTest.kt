package app.cleanroom.streamlab.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionDetectorTest {
    @Test fun mobileNetworkWinsSoCrossingABorderChangesTheCountry() {
        assertEquals("ES", RegionDetector.pick("es", "fr", "FR"))
    }

    @Test fun fallsBackToSimThenDeviceRegion() {
        assertEquals("MA", RegionDetector.pick("", "ma", "FR"))
        assertEquals("FR", RegionDetector.pick(null, null, "fr"))
    }

    @Test fun ignoresInvalidValues() {
        assertNull(RegionDetector.pick("", "x", "419"))
        assertEquals("BE", RegionDetector.pick("eu ", "  be", null))
    }
}
