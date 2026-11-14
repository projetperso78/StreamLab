package app.cleanroom.streamlab.cast

import app.cleanroom.streamlab.data.PlaybackSource
import java.net.URI

data class CastMedia(
    val title: String,
    val url: String,
    val contentType: String,
    val live: Boolean,
)

object CastPolicy {
    fun mediaFor(source: PlaybackSource?, category: String): CastMedia? {
        source ?: return null
        if (source.headers.isNotEmpty()) return null
        val uri = runCatching { URI(source.url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val path = uri.path.orEmpty().lowercase()
        val contentType = when {
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".m4a") -> "audio/mp4"
            category.contains("radio", ignoreCase = true) -> "audio/mpeg"
            else -> return null
        }
        val live = path.endsWith(".m3u8") || path.endsWith(".mpd") ||
            category.contains("radio", ignoreCase = true) || category.contains("live", ignoreCase = true)
        return CastMedia(source.name, source.url, contentType, live)
    }
}
