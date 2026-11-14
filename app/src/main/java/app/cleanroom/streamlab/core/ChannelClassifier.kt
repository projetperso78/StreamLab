package app.cleanroom.streamlab.core

import java.text.Normalizer
import java.util.Locale

/**
 * Infers a channel's country and language from what IPTV playlists usually carry:
 * tvg-country / tvg-language attributes, the tvg-id suffix (e.g. "France24.fr"),
 * name or group prefixes ("FR: TF1", "|AR| MBC", "UK - BBC") and country names in groups.
 *
 * Country and language are kept separate on purpose: a Moroccan channel may be in
 * French or Arabic, so language is only set when the playlist states it.
 */
object ChannelClassifier {
    data class Origin(val country: String?, val language: String?)

    private val isoCountries: Set<String> = Locale.getISOCountries().toSet()
    private val iso3ToIso2: Map<String, String> = isoCountries.associateBy { code ->
        runCatching { Locale.Builder().setRegion(code).build().isO3Country }.getOrDefault("")
    }.filterKeys { it.isNotEmpty() }
    private val countryAliases = mapOf("UK" to "GB", "EN" to "GB", "USA" to "US")

    /** Two/three letter prefixes that IPTV lists use for a language rather than a country. */
    private val languagePrefixes = mapOf("AR" to "ar", "ARA" to "ar", "ARAB" to "ar", "EN" to "en", "ENG" to "en", "FR" to "fr", "FRA" to "fr")

    private val languageNames: Map<String, String> = buildMap {
        for (code in listOf("fr", "en", "ar", "es", "de", "it", "pt", "nl", "tr", "ru", "zh", "hi", "ur", "fa", "he", "pl", "ro", "el", "sv", "ja", "ko")) {
            val locale = Locale.forLanguageTag(code)
            put(normalize(locale.getDisplayLanguage(Locale.ENGLISH)), code)
            put(normalize(locale.getDisplayLanguage(Locale.FRENCH)), code)
            put(code, code)
            runCatching { locale.isO3Language }.getOrNull()?.let { put(it, code) }
        }
        put("arab", "ar"); put("arabe", "ar"); put("anglais", "en")
    }

    private val countryNames: Map<String, String> = buildMap {
        for (code in isoCountries) {
            val locale = Locale.Builder().setRegion(code).build()
            for (display in listOf(Locale.ENGLISH, Locale.FRENCH)) {
                val name = normalize(locale.getDisplayCountry(display))
                if (name.length >= 4) put(name, code)
            }
        }
        put("usa", "US"); put("uk", "GB"); put("emirates", "AE"); put("uae", "AE"); put("ksa", "SA")
    }

    private val prefix = Regex("""^\W*([A-Za-z]{2,4})\s*[|:\-–]""")
    private val bracketPrefix = Regex("""^\s*[|\[(]\s*([A-Za-z]{2,4})\s*[|\])]""")

    fun classify(
        tvgCountry: String? = null,
        tvgLanguage: String? = null,
        tvgId: String? = null,
        group: String? = null,
        name: String? = null,
        /** Country the whole playlist is about, when its address says so (iptv-org ".../countries/fr.m3u"). */
        playlistCountry: String? = null,
    ): Origin {
        val tags = listOfNotNull(name, group).mapNotNull(::prefixTag)
        val language = languageFromAttribute(tvgLanguage)
            ?: tags.firstNotNullOfOrNull { languagePrefixes[it] }
            ?: group?.let(::languageFromWords)
        // Where the channel is broadcast beats who owns it: in "CineActionbyPlutoTV.de@FR" the
        // ".de" is Pluto TV's home, "@FR" is the French feed a French viewer actually gets.
        val country = countryFromAttribute(tvgCountry)
            ?: countryFromFeed(tvgId)
            ?: playlistCountry?.uppercase(Locale.ROOT)?.takeIf { it in isoCountries }
            ?: countryFromTvgId(tvgId)
            ?: tags.firstNotNullOfOrNull(::countryFromTag)
            ?: group?.let(::countryFromWords)
        return Origin(country, language)
    }

    private fun prefixTag(text: String): String? =
        (bracketPrefix.find(text) ?: prefix.find(text))?.groupValues?.get(1)?.uppercase(Locale.ROOT)

    private fun countryFromTag(tag: String): String? = when {
        tag == "AR" -> null // "AR" in IPTV lists means Arabic, not Argentina.
        tag in countryAliases -> countryAliases[tag]
        tag.length == 2 && tag in isoCountries -> tag
        tag.length == 3 -> iso3ToIso2[tag]
        else -> null
    }

    private fun countryFromAttribute(raw: String?): String? = raw
        ?.split(';', ',', '|', ' ')
        ?.map { it.trim().uppercase(Locale.ROOT) }
        ?.firstNotNullOfOrNull { code -> countryAliases[code] ?: code.takeIf { it in isoCountries } ?: iso3ToIso2[code] }

    /** "@FR", "@France", "@Belgium" after the id name the broadcast feed; "@SD"/"@HD" do not. */
    private fun countryFromFeed(raw: String?): String? {
        val feed = raw?.substringAfter('@', "")?.trim().orEmpty()
        if (feed.isEmpty()) return null
        val upper = feed.uppercase(Locale.ROOT)
        // Picture quality, not a place ("SD" would otherwise read as Sudan).
        if (upper in setOf("SD", "HD", "FHD", "UHD", "4K", "8K", "HEVC")) return null
        if (upper.length == 2 && upper in isoCountries) return upper
        return countryNames[normalize(feed)]
    }

    /** iptv-org style address: ".../countries/fr.m3u" lists the channels of that country. */
    fun countryFromPlaylistUrl(url: String?): String? {
        val match = Regex("""(?i)/countries/([a-z]{2})\.m3u8?""").find(url.orEmpty()) ?: return null
        return match.groupValues[1].uppercase(Locale.ROOT).takeIf { it in isoCountries }
    }

    private fun countryFromTvgId(raw: String?): String? {
        val id = raw?.substringBefore('@')?.trim() ?: return null
        if (!id.contains('.')) return null
        val suffix = id.substringAfterLast('.').uppercase(Locale.ROOT)
        return countryAliases[suffix] ?: suffix.takeIf { it.length == 2 && it in isoCountries }
    }

    private fun languageFromAttribute(raw: String?): String? = raw
        ?.split(';', ',', '|')
        ?.firstNotNullOfOrNull { languageNames[normalize(it)] }

    private fun words(text: String): List<String> = normalize(text).split(' ').filter { it.isNotBlank() }

    private fun languageFromWords(text: String): String? = words(text).firstNotNullOfOrNull { word ->
        languageNames[word]?.takeIf { word.length >= 4 }
    }

    private fun countryFromWords(text: String): String? {
        val normalized = normalize(text)
        countryNames[normalized]?.let { return it }
        val tokens = words(text)
        for (size in 3 downTo 1) {
            for (start in 0..tokens.size - size) {
                countryNames[tokens.subList(start, start + size).joinToString(" ")]?.let { return it }
            }
        }
        return null
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
