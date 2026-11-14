package app.cleanroom.streamlab.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SleepTimerTest {
    @Test fun zeroCancelsAndMinutesConvert() {
        assertNull(SleepTimer.delayMs(0))
        assertEquals(15 * 60_000L, SleepTimer.delayMs(15))
    }

    @Test fun rejectsAbsurdDurations() {
        assertThrows(IllegalArgumentException::class.java) { SleepTimer.delayMs(10_000) }
    }

    @Test fun minutesLeftRoundsUp() {
        assertEquals(15, SleepTimer.minutesLeft(15 * 60_000L, 0))
        assertEquals(1, SleepTimer.minutesLeft(1_000L, 0))
        assertEquals(0, SleepTimer.minutesLeft(0, 5))
    }
}
