package app.cleanroom.streamlab.core

import org.junit.Assert.*
import org.junit.Test
import app.cleanroom.streamlab.data.AesGcmCodec
import javax.crypto.KeyGenerator

class SecurityTest {
    @Test fun urlValidation() {
        assertEquals("https://example.org/a", SourcePolicy.requireRemote("https://example.org/a"))
        listOf("http://example.org", "file:///tmp/a", "content://x", "https://localhost/a", "https://127.0.0.1/a", "https://192.168.1.3/a", "https://user:password@example.org/a").forEach {
            assertThrows(IllegalArgumentException::class.java) { SourcePolicy.requireRemote(it) }
        }
        assertEquals("http://example.org/live.m3u8", SourcePolicy.requireMediaRemote("http://example.org/live.m3u8"))
        assertEquals("https://example.org/live.m3u8", SourcePolicy.requireMediaRemote("https://example.org/live.m3u8"))
        listOf("http://localhost/live", "http://127.0.0.1/live", "http://10.0.0.1/live", "http://192.168.1.3/live", "file:///tmp/movie.mp4").forEach {
            assertThrows(IllegalArgumentException::class.java) { SourcePolicy.requireMediaRemote(it) }
        }
        assertEquals("content://media/external/video/media/42", SourcePolicy.requireLocalContent("content://media/external/video/media/42"))
    }
    @Test fun diagnosticsRemoveUrlsAndCredentials() {
        val value = Redaction.diagnostic("Failed https://example.org/live?token=abc123 Authorization: Bearer123 password=secret MAC=AA:BB")
        assertFalse(value.contains("abc123"))
        assertFalse(value.contains("Bearer123"))
        assertFalse(value.contains("secret"))
        assertFalse(value.contains("AA:BB"))
    }
    @Test fun credentialCipherRandomizesAndRejectsTampering() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val clear = "credential-value".toByteArray()
        val first = AesGcmCodec.encrypt(key, clear)
        val second = AesGcmCodec.encrypt(key, clear)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(clear, AesGcmCodec.decrypt(key, first))
        val tampered = first.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { AesGcmCodec.decrypt(key, tampered) }
    }
    @Test fun opaqueMediaIdsNeverAcceptAddressesOrMalformedIds() {
        assertEquals("channel:42", OpaqueMediaId.channel(42))
        assertEquals(42L, OpaqueMediaId.channelId("channel:42"))
        listOf(null, "", "channel:0", "channel:-1", "channel:1/2", "channel: 1", "https://example.org/live", "channel:https://example.org").forEach {
            assertNull(OpaqueMediaId.channelId(it))
        }
    }}
