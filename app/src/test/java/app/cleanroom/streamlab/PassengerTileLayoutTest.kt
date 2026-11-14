package app.cleanroom.streamlab

import org.junit.Assert.assertEquals
import org.junit.Test

class PassengerTileLayoutTest {
    @Test fun columnsAdaptWithoutSqueezingText() {
        assertEquals(1, PassengerTileLayout.columnsForWidth(320))
        assertEquals(2, PassengerTileLayout.columnsForWidth(393))
        assertEquals(2, PassengerTileLayout.columnsForWidth(600))
        assertEquals(3, PassengerTileLayout.columnsForWidth(800))
        assertEquals(4, PassengerTileLayout.columnsForWidth(1280))
        assertEquals(4, PassengerTileLayout.columnsForWidth(1920))
    }
}
