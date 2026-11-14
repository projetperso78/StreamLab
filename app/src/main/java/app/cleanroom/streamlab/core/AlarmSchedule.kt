package app.cleanroom.streamlab.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** When the radio alarm rings next. Pure java.time so DST and day rollover are unit-tested. */
object AlarmSchedule {
    data class Settings(
        val enabled: Boolean = false,
        val hour: Int = 7,
        val minute: Int = 0,
        /** Empty = once (next occurrence of the time), otherwise only these days. */
        val days: Set<DayOfWeek> = emptySet(),
        val mediaId: String? = null,
    ) {
        init {
            require(hour in 0..23 && minute in 0..59) { "Invalid alarm time" }
        }
    }

    val WEEKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.values().toSet()

    /** Next ring strictly after [now], or null when the alarm is off. */
    fun next(settings: Settings, now: ZonedDateTime): ZonedDateTime? {
        if (!settings.enabled) return null
        val time = LocalTime.of(settings.hour, settings.minute)
        val zone: ZoneId = now.zone
        var day: LocalDate = now.toLocalDate()
        repeat(8) {
            // atZone resolves a time skipped by DST forward to the first valid instant after the gap.
            val candidate = day.atTime(time).atZone(zone)
            if (candidate.isAfter(now) && (settings.days.isEmpty() || day.dayOfWeek in settings.days)) return candidate
            day = day.plusDays(1)
        }
        return null
    }

    fun encodeDays(days: Set<DayOfWeek>): String = days.sorted().joinToString(",") { it.value.toString() }
    fun decodeDays(raw: String?): Set<DayOfWeek> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { v -> v in 1..7 }?.let(DayOfWeek::of) }.toSet()
}
