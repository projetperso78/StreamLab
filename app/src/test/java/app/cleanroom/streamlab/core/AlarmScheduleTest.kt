package app.cleanroom.streamlab.core

import app.cleanroom.streamlab.core.AlarmSchedule.Settings
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AlarmScheduleTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, paris)

    @Test fun offMeansNoRing() {
        assertNull(AlarmSchedule.next(Settings(enabled = false), at(2026, 11, 13, 6, 0)))
    }

    @Test fun onceRingsTodayIfStillAheadElseTomorrow() {
        val s = Settings(enabled = true, hour = 7, minute = 30)
        assertEquals(at(2026, 11, 13, 7, 30), AlarmSchedule.next(s, at(2026, 11, 13, 6, 0)))
        assertEquals(at(2026, 11, 14, 7, 30), AlarmSchedule.next(s, at(2026, 11, 13, 7, 30)))
        assertEquals(at(2026, 11, 14, 7, 30), AlarmSchedule.next(s, at(2026, 11, 13, 23, 59)))
    }

    @Test fun weekdaysSkipTheWeekend() {
        val s = Settings(enabled = true, hour = 6, minute = 45, days = AlarmSchedule.WEEKDAYS)
        // Friday 13 Nov 2026 after the alarm -> Monday 16.
        assertEquals(at(2026, 11, 16, 6, 45), AlarmSchedule.next(s, at(2026, 11, 13, 8, 0)))
        assertEquals(DayOfWeek.MONDAY, AlarmSchedule.next(s, at(2026, 11, 14, 5, 0))!!.dayOfWeek)
    }

    @Test fun summerTimeGapMovesTheRingAfterTheGap() {
        // 29 Mar 2026, Paris clocks jump from 02:00 to 03:00: a 02:30 alarm rings at 03:30 local.
        val s = Settings(enabled = true, hour = 2, minute = 30)
        val ring = AlarmSchedule.next(s, at(2026, 3, 29, 0, 0))!!
        assertEquals(3, ring.hour)
        assertEquals(29, ring.dayOfMonth)
    }

    @Test fun daysRoundTripAndRejectGarbage() {
        assertEquals(AlarmSchedule.WEEKDAYS, AlarmSchedule.decodeDays(AlarmSchedule.encodeDays(AlarmSchedule.WEEKDAYS)))
        assertEquals(setOf(DayOfWeek.SUNDAY), AlarmSchedule.decodeDays("7, 9, x,"))
        assertEquals(emptySet<DayOfWeek>(), AlarmSchedule.decodeDays(null))
        assertThrows(IllegalArgumentException::class.java) { Settings(hour = 24) }
    }
}
