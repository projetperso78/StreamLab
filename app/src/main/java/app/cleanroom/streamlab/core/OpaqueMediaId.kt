package app.cleanroom.streamlab.core

/** Opaque media IDs exposed to car/media controllers. Never embeds a source URL or secret. */
object OpaqueMediaId {
    private const val CHANNEL_PREFIX = "channel:"

    fun channel(channelId: Long): String {
        require(channelId > 0) { "Invalid channel id" }
        return CHANNEL_PREFIX + channelId
    }

    fun channelId(mediaId: String?): Long? {
        if (mediaId == null || !mediaId.startsWith(CHANNEL_PREFIX)) return null
        val raw = mediaId.removePrefix(CHANNEL_PREFIX)
        if (raw.isEmpty() || raw.length > 19 || raw.any { !it.isDigit() }) return null
        return raw.toLongOrNull()?.takeIf { it > 0 }
    }
}
