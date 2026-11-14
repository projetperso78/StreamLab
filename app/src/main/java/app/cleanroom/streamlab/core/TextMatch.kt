package app.cleanroom.streamlab.core

import java.text.Normalizer

/** Forgiving search: ignores case, accents and punctuation ("etats unis" finds "États-Unis"). */
object TextMatch {
    private val marks = Regex("""\p{Mn}+""")
    private val separators = Regex("""[\s\-_'’.]+""")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase().replace(separators, " ").trim()

    fun matches(text: String?, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return text != null && normalize(text).contains(q)
    }
}
