package app.cleanroom.streamlab.core

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowNextTest {
    private val h = 3_600_000L
    private val news = NowNext.Slot("Journal", 20 * h, 21 * h)
    private val film = NowNext.Slot("Film", 21 * h, 23 * h)
    private val late = NowNext.Slot("Magazine", 23 * h, 24 * h)

    @Test fun onNowAndNext() {
        val result = NowNext.pick(listOf(film, late, news), 20 * h + 30 * 60_000)
        assertEquals("Journal", result.now?.title)
        assertEquals("Film", result.next?.title)
    }

    @Test fun gapBetweenProgrammesShowsOnlyNext() {
        val result = NowNext.pick(listOf(film), 20 * h)
        assertNull(result.now)
        assertEquals("Film", result.next?.title)
    }

    @Test fun progressIsAShare() {
        assertEquals(0.5f, NowNext.progress(news, 20 * h + 30 * 60_000), 0.001f)
        assertEquals(1f, NowNext.progress(news, 22 * h), 0.001f)
    }

    @Test fun clockIs24h() {
        assertEquals("20:30", NowNext.clock(20 * h + 30 * 60_000, ZoneOffset.UTC))
    }
}
