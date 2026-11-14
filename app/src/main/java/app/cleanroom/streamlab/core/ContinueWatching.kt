package app.cleanroom.streamlab.core

/** "Continue watching" rules shared by the Home row and the player resume. */
object ContinueWatching {
    const val MIN_WATCHED_MS = 10_000L
    const val END_MARGIN_MS = 30_000L

    /** Worth offering: started for real, not finished (last 5 % or 30 s count as finished). */
    fun isResumable(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= MIN_WATCHED_MS &&
            durationMs - positionMs > END_MARGIN_MS && positionMs < durationMs * 95 / 100

    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** Minutes left, rounded up so "less than a minute" still reads 1. */
    fun minutesLeft(positionMs: Long, durationMs: Long): Long =
        ((durationMs - positionMs).coerceAtLeast(0L) + 59_999L) / 60_000L
}
