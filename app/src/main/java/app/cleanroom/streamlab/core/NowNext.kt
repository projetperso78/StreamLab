package app.cleanroom.streamlab.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "On now" and "next" for a live channel, from its programme guide. Pure, unit-tested. */
object NowNext {
    data class Slot(val title: String, val startMs: Long, val endMs: Long)
    data class Result(val now: Slot?, val next: Slot?)

    /** Picks what is on at [nowMs] and what follows, from programmes of one channel in any order. */
    fun pick(programmes: List<Slot>, nowMs: Long): Result {
        val sorted = programmes.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        val current = sorted.lastOrNull { it.startMs <= nowMs && it.endMs > nowMs }
        val next = sorted.firstOrNull { it.startMs >= (current?.endMs ?: nowMs) && it !== current }
        return Result(current, next)
    }

    /** Share of the programme already shown, 0..1. */
    fun progress(slot: Slot, nowMs: Long): Float =
        ((nowMs - slot.startMs).toFloat() / (slot.endMs - slot.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)

    fun clock(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(ms))
}
