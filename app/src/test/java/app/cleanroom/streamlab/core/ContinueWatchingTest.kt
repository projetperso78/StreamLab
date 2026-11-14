package app.cleanroom.streamlab.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingTest {
    private val film = 100 * 60_000L

    @Test fun offersOnlyFilmsStartedAndNotFinished() {
        assertTrue(ContinueWatching.isResumable(42 * 60_000L, film))
        assertFalse("barely started", ContinueWatching.isResumable(5_000L, film))
        assertFalse("credits", ContinueWatching.isResumable(96 * 60_000L, film))
        assertFalse("last 30 s", ContinueWatching.isResumable(film - 20_000L, film))
        assertFalse("live stream, no duration", ContinueWatching.isResumable(60_000L, 0L))
    }

    @Test fun progressAndTimeLeft() {
        assertEquals(0.42f, ContinueWatching.fraction(42 * 60_000L, film), 0.001f)
        assertEquals(58L, ContinueWatching.minutesLeft(42 * 60_000L, film))
        assertEquals(1L, ContinueWatching.minutesLeft(film - 10_000L, film))
    }
}
