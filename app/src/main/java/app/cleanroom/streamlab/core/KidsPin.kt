package app.cleanroom.streamlab.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Parent PIN for kids mode. Only a salted PBKDF2 hash is ever stored, and repeated wrong
 * guesses lock the pad for a while so a child cannot brute-force 10,000 codes.
 */
object KidsPin {
    const val LENGTH = 4
    const val MAX_FAILURES = 5
    const val LOCK_MS = 30_000L
    private const val ITERATIONS = 20_000

    fun isValidFormat(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(16).also(random::nextBytes)

    fun hash(pin: String, salt: ByteArray): ByteArray {
        require(isValidFormat(pin)) { "PIN must be $LENGTH digits" }
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun matches(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        isValidFormat(pin) && MessageDigest.isEqual(hash(pin, salt), expected)

    /** State of the unlock pad after an attempt: failures so far and when the pad unlocks again (0 = open). */
    data class Guard(val failures: Int = 0, val lockedUntil: Long = 0L) {
        fun isLocked(now: Long): Boolean = now < lockedUntil
        fun secondsLeft(now: Long): Long = ((lockedUntil - now).coerceAtLeast(0L) + 999) / 1000
        fun afterFailure(now: Long): Guard {
            val count = failures + 1
            return if (count >= MAX_FAILURES) Guard(0, now + LOCK_MS) else Guard(count, 0L)
        }
    }
}
