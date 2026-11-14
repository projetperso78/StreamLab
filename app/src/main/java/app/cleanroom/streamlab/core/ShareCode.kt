package app.cleanroom.streamlab.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compact, QR-friendly bundle of what a user may share with a friend:
 * favourite radio stations and public M3U playlist links.
 * Never contains credentials, Xtream accounts, custom headers or local files.
 */
object ShareCode {
    const val PREFIX = "SLSHARE1:"
    private const val MAX_RADIOS = 25
    private const val MAX_PLAYLISTS = 5
    private const val MAX_CODE_CHARS = 2_500
    private const val MAX_INFLATED_BYTES = 64_000

    data class Radio(val name: String, val url: String, val country: String?)
    data class Playlist(val name: String, val url: String)
    data class Bundle(val radios: List<Radio>, val playlists: List<Playlist>)

    fun encode(bundle: Bundle): String {
        val radios = bundle.radios.mapNotNull(::safeRadio).take(MAX_RADIOS)
        val playlists = bundle.playlists.mapNotNull(::safePlaylist).take(MAX_PLAYLISTS)
        require(radios.isNotEmpty() || playlists.isNotEmpty()) { "Nothing to share" }
        val json = JSONObject()
            .put("r", JSONArray().apply { radios.forEach { put(JSONArray().put(it.name).put(it.url).put(it.country ?: "")) } })
            .put("p", JSONArray().apply { playlists.forEach { put(JSONArray().put(it.name).put(it.url)) } })
            .toString()
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val code = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        require(code.length <= MAX_CODE_CHARS) { "Too much to share in one code" }
        return code
    }

    fun decode(code: String): Bundle {
        val trimmed = code.trim()
        require(trimmed.startsWith(PREFIX) && trimmed.length <= MAX_CODE_CHARS) { "Not a StreamLab share code" }
        val packed = Base64.getUrlDecoder().decode(trimmed.removePrefix(PREFIX))
        val bytes = InflaterInputStream(ByteArrayInputStream(packed)).use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                require(buffer.size() <= MAX_INFLATED_BYTES) { "Share code too large" }
            }
            buffer.toByteArray()
        }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val radios = json.optJSONArray("r")?.let { array ->
            (0 until minOf(array.length(), MAX_RADIOS)).mapNotNull { i ->
                val item = array.optJSONArray(i) ?: return@mapNotNull null
                safeRadio(Radio(item.optString(0), item.optString(1), item.optString(2).ifBlank { null }))
            }
        }.orEmpty()
        val playlists = json.optJSONArray("p")?.let { array ->
            (0 until minOf(array.length(), MAX_PLAYLISTS)).mapNotNull { i ->
                val item = array.optJSONArray(i) ?: return@mapNotNull null
                safePlaylist(Playlist(item.optString(0), item.optString(1)))
            }
        }.orEmpty()
        return Bundle(radios, playlists)
    }

    private fun cleanName(name: String): String? =
        name.trim().take(80).takeIf { it.isNotEmpty() && it.none(Char::isISOControl) }

    private fun safeRadio(radio: Radio): Radio? {
        val name = cleanName(radio.name) ?: return null
        val url = runCatching { SourcePolicy.requireMediaRemote(radio.url) }.getOrNull() ?: return null
        if (hasSecret(url)) return null
        val country = radio.country?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{2}")) }
        return Radio(name, url, country)
    }

    private fun safePlaylist(playlist: Playlist): Playlist? {
        val name = cleanName(playlist.name) ?: return null
        val url = runCatching { SourcePolicy.requireRemote(playlist.url) }.getOrNull() ?: return null
        if (hasSecret(url)) return null
        return Playlist(name, url)
    }

    /** Refuse links that embed account details (Xtream-style username/password, tokens). */
    private fun hasSecret(url: String): Boolean =
        Regex("(?i)[?&](user(name)?|pass(word)?|token|key|auth|sid|session)=").containsMatchIn(url) ||
            Regex("(?i)/(get\\.php|player_api\\.php)").containsMatchIn(url)
}
