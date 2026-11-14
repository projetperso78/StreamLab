package app.cleanroom.streamlab.services

import app.cleanroom.streamlab.core.FetchGateway
import app.cleanroom.streamlab.core.SourcePolicy
import app.cleanroom.streamlab.core.StationTidy
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class RadioCountry(
    val code: String,
    val stationCount: Int,
) {
    val flag: String get() = code.uppercase().map { char ->
        String(Character.toChars(0x1F1E6 + (char.code - 'A'.code).coerceIn(0, 25)))
    }.joinToString("")
}

data class RadioStation(
    val uuid: String,
    val name: String,
    val countryCode: String,
    val streamUrl: String,
    val favicon: String?,
    val codec: String?,
    val bitrate: Int,
    val votes: Int,
    val tags: String?,
    val language: String? = null,
    /** Other addresses of the same station, tried in turn if the main one fails (two at most). */
    val backups: List<String> = emptyList(),
)

interface RadioDirectoryGateway {
    suspend fun countries(): List<RadioCountry>
    suspend fun stations(countryCode: String, query: String = "", limit: Int = 250): List<RadioStation>
    /** Stations gaining the most listeners right now in this country. */
    suspend fun trending(countryCode: String, limit: Int = 40): List<RadioStation> = emptyList()
}

class RadioBrowserDirectory(private val network: FetchGateway) : RadioDirectoryGateway {
    override suspend fun countries(): List<RadioCountry> = withContext(Dispatchers.IO) {
        val bytes = network.fetchWithHeaders(COUNTRIES_URL, 1_000_000, API_HEADERS)
        parseCountries(bytes)
    }

    override suspend fun stations(countryCode: String, query: String, limit: Int): List<RadioStation> = withContext(Dispatchers.IO) {
        val code = countryCode.trim().uppercase()
        require(code.matches(Regex("[A-Z]{2}"))) { "Code pays invalide" }
        val safeLimit = limit.coerceIn(1, 500)
        val url = "$API_ROOT/json/stations/bycountrycodeexact/${URLEncoder.encode(code, "UTF-8")}?hidebroken=true&order=votes&reverse=true&limit=$safeLimit"
        val bytes = network.fetchWithHeaders(url, 8_000_000, API_HEADERS)
        val needle = query.trim().lowercase()
        tidy(parseStations(bytes), code).asSequence()
            .filter { needle.isBlank() || it.name.lowercase().contains(needle) || it.tags.orEmpty().lowercase().contains(needle) }
            .take(safeLimit)
            .toList()
    }

    override suspend fun trending(countryCode: String, limit: Int): List<RadioStation> = withContext(Dispatchers.IO) {
        val code = countryCode.trim().uppercase()
        require(code.matches(Regex("[A-Z]{2}"))) { "Code pays invalide" }
        val bytes = network.fetchWithHeaders(trendingUrl(code, limit), 4_000_000, API_HEADERS)
        tidy(parseStations(bytes), code).take(limit.coerceIn(1, 100))
    }

    internal fun trendingUrl(code: String, limit: Int): String =
        "$API_ROOT/json/stations/search?countrycode=${URLEncoder.encode(code, "UTF-8")}&hidebroken=true&order=clicktrend&reverse=true&limit=${limit.coerceIn(1, 100)}"

    /**
     * Same country only, readable names, one entry per station (the best-voted copy,
     * since results arrive ordered), and the country's own languages first.
     */
    internal fun tidy(stations: List<RadioStation>, code: String): List<RadioStation> {
        val local = StationTidy.localLanguages(code)
        val unique = stations.asSequence()
            .filter { it.countryCode == code }
            .map { it.copy(name = StationTidy.cleanName(it.name)) }
            .groupBy { StationTidy.duplicateKey(it.name) }
            .values
            .map { copies ->
                // Best-voted copy is shown; the others become its backup streams.
                val best = copies.first()
                best.copy(backups = copies.drop(1).map { it.streamUrl }.filter { it != best.streamUrl }.distinct().take(2))
            }
        val (home, foreign) = unique.partition { StationTidy.isLocal(it.language, local) }
        return home + foreign
    }

    internal fun parseCountries(bytes: ByteArray): List<RadioCountry> {
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val code = item.optString("name").uppercase()
                val count = item.optInt("stationcount", 0)
                if (code.matches(Regex("[A-Z]{2}")) && count > 0) add(RadioCountry(code, count))
            }
        }.sortedBy { it.code }
    }

    internal fun parseStations(bytes: ByteArray): List<RadioStation> {
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                if (item.optInt("lastcheckok", 0) != 1) return@repeat
                val raw = item.optString("url_resolved").ifBlank { item.optString("url") }
                val safe = runCatching { SourcePolicy.requireMediaRemote(raw) }.getOrNull() ?: return@repeat
                val name = item.optString("name").trim()
                val uuid = item.optString("stationuuid").trim()
                if (name.isBlank() || uuid.isBlank()) return@repeat
                val favicon = item.optString("favicon").takeIf { it.isNotBlank() }
                    ?.let { runCatching { SourcePolicy.requireMediaRemote(it) }.getOrNull() }
                add(
                    RadioStation(
                        uuid = uuid,
                        name = name.take(120),
                        countryCode = item.optString("countrycode").uppercase().take(2),
                        streamUrl = safe,
                        favicon = favicon,
                        codec = item.optString("codec").takeIf { it.isNotBlank() },
                        bitrate = item.optInt("bitrate", 0),
                        votes = item.optInt("votes", 0),
                        tags = item.optString("tags").takeIf { it.isNotBlank() },
                        language = item.optString("language").takeIf { it.isNotBlank() },
                    )
                )
            }
        }.distinctBy { it.streamUrl }
    }

    companion object {
        const val API_ROOT = "https://all.api.radio-browser.info"
        const val COUNTRIES_URL = "$API_ROOT/json/countrycodes?hidebroken=true&order=name"
        private val API_HEADERS = mapOf("User-Agent" to "StreamLab/0.1 Android")
    }
}
