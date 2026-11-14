package app.cleanroom.streamlab.data

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The byte envelope used for Keystore-backed profile values: 12-byte IV followed by ciphertext and tag. */
object AesGcmCodec {
    fun encrypt(key: SecretKey, clear: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(clear)
    }

    fun decrypt(key: SecretKey, envelope: ByteArray): ByteArray {
        require(envelope.size >= 29) { "Invalid encrypted value" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(0, 12)))
        return cipher.doFinal(envelope.copyOfRange(12, envelope.size))
    }
}
