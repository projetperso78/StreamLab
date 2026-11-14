package app.cleanroom.streamlab.services

import java.net.URI

data class ExternalService(
    val id: String,
    val name: String,
    val website: URI,
)

object ServiceRegistry {
    /** Official Android application IDs used only for explicit hand-off to installed publisher apps. */
    val officialPackages: Map<String, String> = mapOf(
        "youtube" to "com.google.android.youtube",
        "netflix" to "com.netflix.mediaclient",
        "prime_video" to "com.amazon.avod.thirdpartyclient",
        "disney_plus" to "com.disney.disneyplus",
        "twitch" to "tv.twitch.android.app",
        "plex" to "com.plexapp.android",
        "jellyfin" to "org.jellyfin.mobile",
        "emby" to "com.mb.android",
    )

    fun validatedWebsite(raw: String): URI {
        val uri = try { URI(raw) } catch (_: Exception) { throw IllegalArgumentException("Invalid service website") }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null) {
            "Service website must be a plain HTTPS address"
        }
        return uri
    }

    private fun service(id: String, name: String, website: String) =
        ExternalService(id, name, validatedWebsite(website))

    val all: List<ExternalService> = listOf(
        service("youtube", "YouTube", "https://www.youtube.com/"),
        service("netflix", "Netflix", "https://www.netflix.com/"),
        service("prime_video", "Prime Video", "https://www.primevideo.com/"),
        service("disney_plus", "Disney+", "https://www.disneyplus.com/"),
        service("twitch", "Twitch", "https://www.twitch.tv/"),
        service("plex", "Plex", "https://app.plex.tv/desktop/"),
        service("jellyfin", "Jellyfin", "https://jellyfin.org/"),
        service("emby", "Emby", "https://emby.media/"),
    )

    init {
        require(all.map { it.id }.toSet().size == all.size)
        require(all.map { it.website }.toSet().size == all.size)
    }
}
