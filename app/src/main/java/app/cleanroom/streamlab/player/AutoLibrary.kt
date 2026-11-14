package app.cleanroom.streamlab.player

import app.cleanroom.streamlab.core.TextMatch
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.data.CatalogChannelRow
import app.cleanroom.streamlab.data.ProfileEntity
import app.cleanroom.streamlab.data.ProfileType
import java.util.Locale

/** Public, URL-free projection of the private Room catalog for car browsers. */
class AutoLibrary(profiles: List<ProfileEntity>, channels: List<CatalogChannelRow>) {
    data class Node(val id: String, val title: String, val browsable: Boolean, val playable: Boolean)

    private val profiles = profiles.filterNot { it.archived }
    private val browseProfiles = this.profiles.filterNot { it.type == ProfileType.LOCAL_MEDIA.name }
    private val radioProfileIds = browseProfiles
        .filter { it.name.trim().startsWith("Radio", ignoreCase = true) }
        .mapTo(HashSet()) { it.id }
    private val audioChannels = channels.filter { row -> browseProfiles.any { it.id == row.profileId } }
    private val radioChannels = audioChannels.filter(::isRadioChannel)
    private val tvChannels = audioChannels.filterNot { it in radioChannels }
    private val byId = audioChannels.associateBy { it.id }

    fun root() = Node(ROOT, "StreamLab", browsable = true, playable = false)

    private fun rootNode(id: String) = Node(id, when (id) {
        TV -> "TV"
        RADIO -> "Radio"
        FAVORITES -> "Favoris"
        RECENT -> "Récents"
        else -> "Sources"
    }, true, false)

    fun children(parentId: String): List<Node> = when (parentId) {
        // Radio first: it works from the very first launch. TV only shows once the user added a list,
        // so a fresh install never opens on an empty "TV" tab in the car.
        ROOT -> listOfNotNull(
            rootNode(RADIO),
            rootNode(TV).takeIf { tvChannels.isNotEmpty() },
            rootNode(FAVORITES),
            rootNode(RECENT),
            rootNode(PROFILES),
        )
        TV -> tvCountries().let { codes ->
            if (codes.size < 2) tvChannels.map(::channelNode)
            else codes.map { code ->
                if (code == OTHER_COUNTRY) Node(TV_COUNTRY_PREFIX + OTHER_COUNTRY, "Autres pays", true, false)
                else Node(TV_COUNTRY_PREFIX + code, countryTitle(code), true, false)
            }
        }
        RADIO -> radioChannels
            .mapNotNull(::radioCountryCode)
            .distinct()
            .sortedBy(::countryName)
            .map { code -> Node(radioCountryId(code), countryTitle(code), true, false) }
        FAVORITES -> audioChannels.filter { it.favorite }.map(::channelNode)
        RECENT -> audioChannels.filter { it.playedAt != null }.sortedByDescending { it.playedAt }.map(::channelNode)
        PROFILES -> browseProfiles.map { Node("profile:${it.id}", safeTitle(it.name, "Source"), true, false) }
        else -> when {
            parentId.startsWith(TV_COUNTRY_PREFIX) -> {
                val code = parentId.removePrefix(TV_COUNTRY_PREFIX)
                tvChannels.filter { (it.country ?: OTHER_COUNTRY) == code }.map(::channelNode)
            }
            parentId.startsWith(RADIO_COUNTRY_PREFIX) -> {
                val code = countryCode(parentId)
                if (code == null) emptyList()
                else radioChannels.filter { radioCountryCode(it) == code }.map(::channelNode)
            }
            parentId.startsWith("profile:") -> {
                val profileId = parentId.removePrefix("profile:").toLongOrNull()
                if (browseProfiles.none { it.id == profileId }) emptyList()
                else groups(profileId!!).mapIndexed { index, name -> Node("group:$profileId:$index", safeTitle(name, "Autres"), true, false) }
            }
            parentId.startsWith("group:") -> {
                val pieces = parentId.split(':')
                val profileId = pieces.getOrNull(1)?.toLongOrNull()
                val index = pieces.getOrNull(2)?.toIntOrNull()
                val name = if (profileId != null && index != null) groups(profileId).getOrNull(index) else null
                if (name == null || browseProfiles.none { it.id == profileId }) emptyList()
                else audioChannels.filter { it.profileId == profileId && it.category == name }.map(::channelNode)
            }
            else -> emptyList()
        }
    }

    fun item(mediaId: String): Node? = when (mediaId) {
        ROOT -> root()
        TV, RADIO, FAVORITES, RECENT, PROFILES -> rootNode(mediaId)
        else -> {
            val channelId = OpaqueMediaId.channelId(mediaId)
            when {
                channelId != null && byId.containsKey(channelId) -> channelNode(byId.getValue(channelId))
                mediaId.startsWith(RADIO_COUNTRY_PREFIX) -> children(RADIO).firstOrNull { it.id == mediaId }
                mediaId.startsWith(TV_COUNTRY_PREFIX) -> children(TV).firstOrNull { it.id == mediaId }
                mediaId.startsWith("profile:") -> children(PROFILES).firstOrNull { it.id == mediaId }
                mediaId.startsWith("group:") -> {
                    val profileId = mediaId.split(':').getOrNull(1)
                    children("profile:$profileId").firstOrNull { it.id == mediaId }
                }
                else -> null
            }
        }
    }

    fun search(query: String): List<Node> {
        val term = query.trim().take(100)
        val matches = if (term.isEmpty()) {
            audioChannels.filter { it.playedAt != null }.sortedByDescending { it.playedAt } + audioChannels.filter { it.favorite }
        } else audioChannels.filter { TextMatch.matches(it.name, term) || TextMatch.matches(it.category, term) }
        return matches.distinctBy { it.id }.take(100).map(::channelNode)
    }

    /** Channels of the same list (radio country, or same source and category) for next/previous. */
    fun siblings(mediaId: String): List<String> {
        val row = OpaqueMediaId.channelId(mediaId)?.let { byId[it] } ?: return emptyList()
        val country = radioCountryCode(row)
        val list = when {
            country != null -> radioChannels.filter { radioCountryCode(it) == country }
            row.country != null -> tvChannels.filter { it.country == row.country }
            else -> audioChannels.filter { it.profileId == row.profileId && it.category == row.category }
        }
        return list.map { OpaqueMediaId.channel(it.id) }
    }

    fun channelId(mediaId: String): Long? = OpaqueMediaId.channelId(mediaId)?.takeIf { byId.containsKey(it) }

    fun isRadioChannel(row: CatalogChannelRow): Boolean =
        row.profileId in radioProfileIds || radioCountryCode(row) != null

    /** Countries of the user's TV channels, most channels first; unknown origin last. */
    private fun tvCountries(): List<String> {
        val counts = tvChannels.groupingBy { it.country ?: OTHER_COUNTRY }.eachCount()
        return counts.keys.sortedWith(compareBy<String> { it == OTHER_COUNTRY }.thenByDescending { counts[it] }.thenBy { countryName(it) })
    }

    private fun groups(profileId: Long) = audioChannels.filter { it.profileId == profileId }.map { it.category }.distinct()

    private fun channelNode(row: CatalogChannelRow) =
        Node(OpaqueMediaId.channel(row.id), safeTitle(row.name, "Chaîne"), browsable = false, playable = true)

    private fun radioCountryCode(row: CatalogChannelRow): String? {
        val category = RADIO_CATEGORY.matchEntire(row.category.trim())?.groupValues?.getOrNull(1)?.uppercase()
        if (category != null) return category
        val profile = browseProfiles.firstOrNull { it.id == row.profileId } ?: return null
        if (!profile.name.trim().startsWith("Radio", ignoreCase = true)) return null
        return COUNTRY_SUFFIX.find(profile.name.trim())?.groupValues?.getOrNull(1)?.uppercase()
    }

    companion object {
        const val ROOT = "root"
        const val TV = "tv"
        const val RADIO = "radio"
        const val FAVORITES = "favorites"
        const val RECENT = "recent"
        const val PROFILES = "profiles"
        const val RADIO_COUNTRY_PREFIX = "radio-country:"
        const val TV_COUNTRY_PREFIX = "tv-country:"
        private const val OTHER_COUNTRY = "other"

        private val sensitive = Regex("(?i)(://|\\b(?:password|passwd|token|secret|api[_-]?key|authorization|cookie|user(?:name)?)\\s*[:=])")
        private val RADIO_CATEGORY = Regex("(?i)^Radio\\s+([A-Z]{2})$")
        private val COUNTRY_SUFFIX = Regex("(?i)([A-Z]{2})$")

        fun radioCountryId(code: String): String = RADIO_COUNTRY_PREFIX + code.uppercase()
        fun countryCode(mediaId: String): String? = mediaId
            .takeIf { it.startsWith(RADIO_COUNTRY_PREFIX) }
            ?.removePrefix(RADIO_COUNTRY_PREFIX)
            ?.uppercase()
            ?.takeIf { it.matches(Regex("[A-Z]{2}")) }

        fun countryName(code: String): String = runCatching {
            Locale.Builder().setRegion(code.uppercase()).build().displayCountry.ifBlank { code.uppercase() }
        }.getOrDefault(code.uppercase())

        fun countryTitle(code: String): String = "${countryFlag(code)} ${countryName(code)}"

        private fun countryFlag(code: String): String = code.uppercase().take(2).map { char ->
            String(Character.toChars(0x1F1E6 + (char.code - 'A'.code).coerceIn(0, 25)))
        }.joinToString("")

        fun safeTitle(value: String, fallback: String): String {
            val title = value.trim()
            return if (title.isEmpty() || title.length > 100 || sensitive.containsMatchIn(title) ||
                title.contains('@') || title.any { it.isISOControl() }) fallback else title
        }
    }
}
