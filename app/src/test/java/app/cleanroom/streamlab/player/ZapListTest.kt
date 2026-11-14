package app.cleanroom.streamlab.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZapListTest {
    private val stations = listOf("a", "b", "c")

    @Test fun nextAndPreviousWrapAround() {
        assertEquals("b", ZapList.step(stations, "a", 1))
        assertEquals("a", ZapList.step(stations, "c", 1))
        assertEquals("c", ZapList.step(stations, "a", -1))
    }

    @Test fun noZapWithoutAListOrForAnUnknownItem() {
        assertNull(ZapList.step(listOf("a"), "a", 1))
        assertNull(ZapList.step(stations, "z", 1))
        assertNull(ZapList.step(stations, null, 1))
    }

    @Test fun nearCountryComesFirst() {
        assertEquals(listOf("FR", "BE", "MA"), ZapList.nearFirst(listOf("BE", "FR", "MA")) { it == "FR" })
        assertEquals(listOf("BE", "MA"), ZapList.nearFirst(listOf("BE", "MA")) { it == "FR" })
    }
}
