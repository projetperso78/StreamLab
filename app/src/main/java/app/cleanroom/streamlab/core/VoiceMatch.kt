package app.cleanroom.streamlab.core

/**
 * "Ok Google, mets RTL sur StreamLab": turns what the assistant passes into a station name and
 * picks the best station for it. Pure, unit-tested.
 */
object VoiceMatch {
    private val filler = Regex(
        """(?i)^\s*(mets|mettre|joue|jouer|lance|lancer|écoute|ecoute|écouter|ecouter|play|put on|listen to|ouvre)\s+""",
    )
    // Only spoken lead-ins: a bare "Radio" belongs to names such as Radio Classique or Radio Nova.
    private val articles = Regex("""(?i)^\s*(la radio|la station|la chaîne|la chaine)\s+""")
    private val tail = Regex("""(?i)\s+(sur|on|dans|in)\s+stream\s*lab.*$""")

    /** "mets la radio RTL sur StreamLab" -> "RTL". */
    fun clean(query: String): String {
        var q = query.trim().take(100)
        q = tail.replace(q, "")
        q = filler.replace(q, "")
        q = articles.replace(q, "")
        return q.trim().trimEnd('.', '!', '?')
    }

    /**
     * Index of the best name for [query]: same name first ("RTL" before "RTL2"), then a name that
     * starts with it, then one that contains it; -1 when nothing matches.
     */
    fun best(names: List<String>, query: String): Int {
        val q = TextMatch.normalize(clean(query))
        if (q.isEmpty()) return -1
        val normalized = names.map { TextMatch.normalize(it) }
        normalized.indexOfFirst { it == q }.takeIf { it >= 0 }?.let { return it }
        normalized.indexOfFirst { it.startsWith("$q ") }.takeIf { it >= 0 }?.let { return it }
        normalized.indexOfFirst { it.startsWith(q) }.takeIf { it >= 0 }?.let { return it }
        return normalized.indexOfFirst { it.contains(q) }
    }
}
