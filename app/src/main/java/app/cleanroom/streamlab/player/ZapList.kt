package app.cleanroom.streamlab.player

/** Next / previous item in the list the listener chose from (steering-wheel skip buttons). */
object ZapList {
    fun step(ids: List<String>, current: String?, delta: Int): String? {
        if (ids.size < 2 || current == null) return null
        val index = ids.indexOf(current)
        if (index < 0) return null
        return ids[Math.floorMod(index + delta, ids.size)]
    }

    /** Puts the user's current country first in the radio country list, marked as "near you". */
    fun <T> nearFirst(items: List<T>, isNear: (T) -> Boolean): List<T> {
        val near = items.filter(isNear)
        return near + items.filterNot(isNear)
    }
}
