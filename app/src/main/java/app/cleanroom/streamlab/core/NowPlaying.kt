package app.cleanroom.streamlab.core

/**
 * Song line sent by a radio stream (ICY "StreamTitle", usually "Artist - Title").
 * Returns null when the stream sends nothing useful (empty, the station name again, a URL, ads ids).
 */
object NowPlaying {
    private val junk = Regex("""^[\s\-_.|:0-9]*$""")

    fun songLine(streamTitle: CharSequence?, stationName: String?): String? {
        val text = streamTitle?.toString()?.replace(Regex("\\s+"), " ")?.trim()?.trim('-', ' ', '|') ?: return null
        if (text.length < 3 || junk.matches(text)) return null
        if (text.startsWith("http", ignoreCase = true) || text.contains("://")) return null
        if (stationName != null && text.equals(stationName.trim(), ignoreCase = true)) return null
        return text.take(120)
    }

    /** "Artist - Title" split for display; null artist when the radio sends a single string. */
    fun split(line: String): Pair<String?, String> {
        val parts = line.split(" - ", limit = 2)
        return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0].trim() to parts[1].trim()
        else null to line
    }
}
