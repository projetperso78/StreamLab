package app.cleanroom.streamlab.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsPinTest {
    @Test fun onlyFourDigitsAreAccepted() {
        assertTrue(KidsPin.isValidFormat("0427"))
        listOf("", "123", "12345", "12a4", "١٢٣٤", " 123").forEach { assertFalse(it, KidsPin.isValidFormat(it)) }
        assertThrows(IllegalArgumentException::class.java) { KidsPin.hash("12", KidsPin.newSalt()) }
    }

    @Test fun hashIsSaltedAndVerifiable() {
        val salt = KidsPin.newSalt()
        val stored = KidsPin.hash("2580", salt)
        assertTrue(KidsPin.matches("2580", salt, stored))
        assertFalse(KidsPin.matches("2581", salt, stored))
        assertFalse(KidsPin.matches("abcd", salt, stored))
        assertFalse("same PIN with another salt must differ", KidsPin.hash("2580", KidsPin.newSalt()).contentEquals(stored))
        assertArrayEquals(stored, KidsPin.hash("2580", salt))
    }

    @Test fun fiveWrongGuessesLockThePadForThirtySeconds() {
        var guard = KidsPin.Guard()
        repeat(4) { guard = guard.afterFailure(1_000L) }
        assertFalse(guard.isLocked(1_000L))
        guard = guard.afterFailure(1_000L)
        assertTrue(guard.isLocked(1_000L))
        assertEquals(30L, guard.secondsLeft(1_000L))
        assertFalse(guard.isLocked(1_000L + KidsPin.LOCK_MS))
        assertEquals(0, guard.failures)
    }
}
