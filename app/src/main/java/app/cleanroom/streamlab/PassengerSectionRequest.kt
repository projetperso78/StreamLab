package app.cleanroom.streamlab

/** A new external request must reopen its screen even when the same screen was previously requested. */
internal object PassengerSectionRequest {
    private val allowed = setOf("Apps", "Library", "Live", "Radio", "Videos", "Favorites", "More", "Guide", "Settings", "Drive", "RadioNear", "Share", "Alarm")

    fun initial(section: String?): Pair<String, Int> =
        (section?.takeIf { it in allowed } ?: "Apps") to 0

    fun next(previous: Pair<String, Int>, section: String?): Pair<String, Int> =
        section?.takeIf { it in allowed }?.let { it to (previous.second + 1) } ?: previous
}
