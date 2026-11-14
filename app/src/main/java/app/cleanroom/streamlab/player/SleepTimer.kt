package app.cleanroom.streamlab.player

/** Sleep timer choices offered in the UI and accepted by the playback service. */
object SleepTimer {
    const val COMMAND = "app.cleanroom.streamlab.SLEEP_TIMER"
    const val ARG_MINUTES = "minutes"
    val choices = listOf(0, 15, 30, 60, 90)
    private const val MAX_MINUTES = 180

    /** Delay before pausing, or null to cancel. Rejects negative or absurd values. */
    fun delayMs(minutes: Int): Long? = when {
        minutes <= 0 -> null
        minutes > MAX_MINUTES -> throw IllegalArgumentException("Sleep timer too long")
        else -> minutes * 60_000L
    }

    /** Whole minutes left, rounded up, for display. */
    fun minutesLeft(endAtMs: Long, nowMs: Long): Int =
        if (endAtMs <= nowMs) 0 else (((endAtMs - nowMs) + 59_999L) / 60_000L).toInt()
}
