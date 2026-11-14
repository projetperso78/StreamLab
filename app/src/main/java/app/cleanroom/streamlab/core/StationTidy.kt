package app.cleanroom.streamlab.core

import java.util.Locale

/**
 * Turns the community radio directory into something a listener can read:
 * clean names, a genre line instead of codec jargon, no duplicates, and the
 * country's own-language stations before the foreign-language ones.
 */
object StationTidy {
    private val technicalWords = Regex(
        """(?i)\b(\d{2,4}\s?k(bps|b/s|bit)?|mp3|aac\+?|ogg|opus|flac|hls|hifi|hi-fi|lofi|lo-fi|low(er)?\s+bitrate|hq|hd|stereo|mono|\d{3,4}[pi]|geo-?blocked|not\s+24/7)\b""",
    )
    private val bracketed = Regex("""\s*[(\[]([^)\]]*)[)\]]""")
    // Only explicit web addresses: "Jazzradio.fr" is a real brand name and stays.
    private val urls = Regex("""(?i)\s*[-–|:]?\s*(https?://\S+|www\.\S+)""")
    private val separators = Regex("""\s*[|•]\s*""")
    private val spaces = Regex("""\s{2,}""")

    /** Readable station name. Never returns blank: falls back to the raw name. */
    fun cleanName(raw: String): String {
        var name = raw.filterNot { isPictograph(it) }.trim()
        // Drop bracketed parts that only carry technical details: "(128.mp3)", "(lower bitrate)".
        name = bracketed.replace(name) { match ->
            val inside = match.groupValues[1]
            if (technicalWords.containsMatchIn(inside) || inside.isBlank()) "" else match.value
        }
        name = urls.replace(name, "")
        name = technicalWords.replace(name, "")
        name = separators.replace(name, " · ")
        name = spaces.replace(name, " ").trim().trim('-', '–', '·', ':', ' ')
        name = softenShouting(name)
        return name.ifBlank { raw.trim() }.take(60)
    }

    /** TV playlists use the same noise: "TF1 (1080p) [Geo-blocked]" reads "TF1". */
    fun cleanChannelName(raw: String): String = cleanName(raw)

    /** "LOVE RADIO" → "Love Radio"; short acronyms such as RTL, FIP or NRJ stay upper case. */
    private fun softenShouting(name: String): String {
        val letters = name.filter { it.isLetter() }
        if (letters.length < 6 || letters.any { it.isLowerCase() }) return name
        return name.split(' ').joinToString(" ") { word ->
            if (word.count { it.isLetter() } <= 3) word
            else word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    private fun isPictograph(char: Char): Boolean {
        if (char.isSurrogate()) return true // emoji live outside the basic plane
        val type = Character.getType(char)
        return type == Character.OTHER_SYMBOL.toInt() || char == '️'
    }

    private val technicalTags = setOf(
        "aac", "aac+", "mp3", "ogg", "opus", "flac", "hls", "radio", "music", "musique", "fm", "am", "webradio",
        "web radio", "internet radio", "public radio", "radio france", "stereo", "hd", "128", "320", "online",
        "france", "french", "français", "information radio", "station", "live",
    )

    /** At most two listener-friendly genres from the free-form tags. */
    fun genres(tags: String?, max: Int = 2): String =
        tags.orEmpty().split(',')
            .map { it.trim() }
            .filter { it.length in 2..24 && it.lowercase(Locale.ROOT) !in technicalTags && it.none(Char::isDigit) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(max)
            .joinToString(" · ") { tag -> tag.replaceFirstChar { it.titlecase(Locale.ROOT) } }

    /** Languages spoken in a country, as the directory spells them ("french", "arabic"). */
    fun localLanguages(countryCode: String): Set<String> =
        Locale.getAvailableLocales()
            .filter { it.country.equals(countryCode, ignoreCase = true) && it.language.isNotBlank() }
            .map { it.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT) }
            .toSet()

    /** True when the station speaks one of the country's languages, or says nothing about it. */
    fun isLocal(language: String?, local: Set<String>): Boolean {
        val spoken = language.orEmpty().split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }
        return spoken.isEmpty() || local.isEmpty() || spoken.any { it in local }
    }

    /** Identity used to merge duplicates: "France Info", "france info (lofi)" and "FRANCE INFO" are one station. */
    fun duplicateKey(name: String): String = TextMatch.normalize(cleanName(name))
}
