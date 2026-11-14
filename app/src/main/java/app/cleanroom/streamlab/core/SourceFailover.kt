package app.cleanroom.streamlab.core

/**
 * A channel can carry up to [MAX_SOURCES] stream addresses: the main one plus backups.
 * When one fails the player moves to the next, without the listener doing anything.
 */
object SourceFailover {
    const val MAX_SOURCES = 3
    private const val MAX_BACKUPS = MAX_SOURCES - 1

    /** Backups as stored in the channel's (encrypted) backup field: one address per line. */
    fun encode(backups: List<String>): String =
        backups.map { it.trim() }.filter { it.isNotEmpty() && '\n' !in it }.distinct().take(MAX_BACKUPS).joinToString("\n")

    fun decode(stored: String?): List<String> =
        stored.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_BACKUPS)

    /** Main address first, then backups; never more than [MAX_SOURCES], never twice the same. */
    fun ordered(main: String, backups: List<String>): List<String> =
        (listOf(main) + backups).distinct().take(MAX_SOURCES)

    /** The source to try after [failedIndex] failed, or null when all have been tried. */
    fun nextIndex(failedIndex: Int, count: Int): Int? = (failedIndex + 1).takeIf { it < count.coerceAtMost(MAX_SOURCES) }

    private val qualityTags = Regex("""(?i)[\s\-_|]*(\(|\[)?\b(u?hd|fhd|sd|4k|uhd|hevc|h265|720p?|1080p?|576p?|480p?|backup|alt|bis|secours|geo[-\s]?blocked|not 24/7)\b(\)|\])?""")

    /**
     * Two playlist entries describe the same channel when they share an EPG id, or the same
     * name once quality tags are removed ("TF1 HD" and "TF1 (backup)" are TF1).
     */
    fun sameChannelKey(name: String, tvgId: String?): String {
        val id = tvgId?.trim()?.lowercase().orEmpty()
        if (id.isNotEmpty()) return "id:$id"
        return "name:" + TextMatch.normalize(qualityTags.replace(name, " "))
    }

    /** For each entry (by index), up to two other entries' addresses for the same channel. */
    fun backupsByIndex(entries: List<Pair<String, String>>): List<List<String>> {
        val byKey = entries.withIndex().groupBy({ it.value.first }, { it.index })
        return entries.mapIndexed { index, (key, url) ->
            byKey[key].orEmpty().filter { it != index }.map { entries[it].second }.filter { it != url }.distinct().take(MAX_BACKUPS)
        }
    }
}
