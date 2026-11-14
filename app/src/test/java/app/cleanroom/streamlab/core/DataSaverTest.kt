package app.cleanroom.streamlab.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DataSaverTest {
    @Test fun capsVideoOnlyOnMobileDataWithTheOptionOn() {
        assertEquals(DataSaver.MOBILE, DataSaver.limits(enabled = true, metered = true))
        assertEquals(DataSaver.NONE, DataSaver.limits(enabled = true, metered = false))
        assertEquals(DataSaver.NONE, DataSaver.limits(enabled = false, metered = true))
    }

    @Test fun mobileCapIs480p() {
        assertEquals(480, DataSaver.MOBILE.maxVideoHeight)
        assertEquals(1_200_000, DataSaver.MOBILE.maxVideoBitrate)
    }
}
