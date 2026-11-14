package app.cleanroom.streamlab.core

import java.io.Reader

data class ParsedChannel(
    val name: String,
    val url: String,
    val group: String,
    val tvgId: String?,
    val tvgName: String?,
    val logo: String?,
    val headers: Map<String, String>,
    val tvgCountry: String? = null,
    val tvgLanguage: String? = null,
)

class M3uParser(private val maxChars: Int = 8_000_000, private val maxChannels: Int = 20_000, private val allowBundledFixture: Boolean = false) {
    private val attribute = Regex("([A-Za-z][A-Za-z0-9-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))")

    fun parse(reader: Reader): List<ParsedChannel> {
        val result = ArrayList<ParsedChannel>()
        val seen = HashSet<String>()
        val input = reader.buffered()
        var total = 0
        var metadata: Map<String, String> = emptyMap()
        var pendingName: String? = null
        var headers = mutableMapOf<String, String>()
        while (true) {
            val line = StringBuilder()
            var ended = false
            while (true) {
                val ch = input.read()
                if (ch < 0) { ended = true; break }
                total++
                require(total <= maxChars && line.length <= 16_384) { "Playlist exceeds size limit" }
                if (ch == '\n'.code) break
                line.append(ch.toChar())
            }
            if (ended && line.isEmpty()) break
            val value = line.toString().trim().removePrefix("\uFEFF")
            when {
                value.startsWith("#EXTINF:", true) -> {
                    val pairs = attribute.findAll(value).associate { m -> m.groupValues[1].lowercase() to (m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }) }
                    metadata = pairs
                    pendingName = value.substringAfterLast(',', "").trim().ifBlank { pairs["tvg-name"] }
                    headers = mutableMapOf()
                    pairs["http-user-agent"]?.takeIf { it.length <= 512 }?.let { headers["User-Agent"] = it }
                    pairs["http-referrer"]?.takeIf { it.length <= 2048 }?.let { headers["Referer"] = it }
                }
                value.startsWith("#EXTVLCOPT:", true) -> {
                    val option = value.substringAfter(':').split('=', limit = 2)
                    if (option.size == 2 && option[1].length <= 2048) when (option[0].lowercase()) {
                        "http-user-agent" -> headers["User-Agent"] = option[1]
                        "http-referrer" -> headers["Referer"] = option[1]
                        "http-origin" -> headers["Origin"] = option[1]
                        "http-header" -> {
                            val pair = option[1].split(':', limit = 2)
                            if (pair.size == 2 && HeaderPolicy.valid(pair[0].trim(), pair[1].trim())) headers[pair[0].trim()] = pair[1].trim()
                        }
                    }
                }
                value.isBlank() || value.startsWith('#') -> Unit
                else -> {
                    val safeUrl = if (allowBundledFixture && value == "fixture://demo.mp4") value else runCatching { SourcePolicy.requireMediaRemote(value) }.getOrNull()
                    if (safeUrl != null && seen.add(safeUrl)) {
                        require(result.size < maxChannels) { "Too many channels" }
                        result += ParsedChannel(
                            name = pendingName?.take(256)?.ifBlank { null } ?: "Channel ${result.size + 1}",
                            url = safeUrl,
                            group = metadata["group-title"]?.take(128) ?: "Other",
                            tvgId = metadata["tvg-id"]?.take(256),
                            tvgName = metadata["tvg-name"]?.take(256),
                            logo = metadata["tvg-logo"]?.takeIf { runCatching { SourcePolicy.requireRemote(it) }.isSuccess },
                            headers = HeaderPolicy.sanitize(headers),
                            tvgCountry = metadata["tvg-country"]?.take(64),
                            tvgLanguage = metadata["tvg-language"]?.take(64),
                        )
                    }
                    metadata = emptyMap()
                    pendingName = null
                    headers = mutableMapOf()
                }
            }
        }
        return result
    }
}
