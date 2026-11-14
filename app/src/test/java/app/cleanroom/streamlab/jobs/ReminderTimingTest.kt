package app.cleanroom.streamlab.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimingTest {
    private val now = 1_000_000_000L

    @Test fun notifiesFiveMinutesBefore() {
        assertEquals(25 * 60_000L, ReminderTiming.delayMs(now + 30 * 60_000L, now))
    }

    @Test fun startingVerySoonNotifiesAtOnce() {
        assertEquals(0L, ReminderTiming.delayMs(now + 2 * 60_000L, now))
    }

    @Test fun startedProgrammeCannotBeReminded() {
        assertNull(ReminderTiming.delayMs(now, now))
        assertNull(ReminderTiming.delayMs(now - 1, now))
    }

    @Test fun keyIsStablePerChannelAndStart() {
        assertEquals("42:1700000000000", ReminderTiming.key(42, 1_700_000_000_000L))
    }
}
