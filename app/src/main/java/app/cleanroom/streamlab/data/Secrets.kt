package app.cleanroom.streamlab.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class Secrets {
    private val alias = "streamlab.profile.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        return Base64.encodeToString(AesGcmCodec.encrypt(key(), value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        return AesGcmCodec.decrypt(key(), bytes).toString(Charsets.UTF_8)
    }
}
