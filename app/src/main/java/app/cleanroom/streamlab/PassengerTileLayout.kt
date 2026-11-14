package app.cleanroom.streamlab

/** Stable responsive columns; never squeeze cards into narrow cells. */
object PassengerTileLayout {
    fun columnsForWidth(widthDp: Int): Int = when {
        widthDp >= 1080 -> 4
        widthDp >= 720 -> 3
        widthDp >= 360 -> 2
        else -> 1
    }
}
