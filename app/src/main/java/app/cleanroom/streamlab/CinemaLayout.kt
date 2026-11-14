package app.cleanroom.streamlab

/**
 * Resolve layout from available dp and system font scale, not pixel resolution.
 * At a font scale of 200%, prefer legibility over density.
 */
internal data class CinemaLayout(
    val splitHero: Boolean,
    val featureColumns: Int,
    val serviceColumns: Int,
    val heroWidthDp: Int,
    val navigationColumns: Int,
    val compactHero: Boolean,
    val iconOnlyNavigation: Boolean,
) {
    val gridColumns: Int get() = if (serviceColumns == 3) 6 else serviceColumns
    val featureSpan: Int get() = gridColumns / featureColumns
    val serviceSpan: Int get() = gridColumns / serviceColumns

    companion object {
        /** Reserve real dp slack for padding, focus outlines and fractional pixel rounding. */
        fun navigationCellWidthDp(widthDp: Int, columns: Int): Int =
            ((widthDp - 12 - 4 * (columns - 1)) / columns).coerceAtLeast(48)

        fun forViewport(
            widthDp: Int,
            fontScale: Float,
            heightDp: Int = Int.MAX_VALUE,
        ): CinemaLayout {
            val scale = fontScale.coerceIn(1f, 3f)
            val sideBySide = widthDp >= 780 && heightDp < widthDp * .85f && scale <= 1.35f
            val heroDp = if (sideBySide) (widthDp * .29f).toInt().coerceIn(240, 330) else 0
            val usableDp = ((widthDp - heroDp) / scale).toInt()
            val services = when {
                usableDp >= 900 -> 4
                usableDp >= 570 -> 3
                usableDp >= 375 -> 2
                else -> 1
            }
            val features = if (usableDp >= 560) 2 else 1
            val iconOnly = widthDp < 420 && scale >= 1.3f
            val navColumns = 5
            return CinemaLayout(
                sideBySide, features, services, heroDp, navColumns,
                compactHero = !sideBySide && widthDp <= 360,
                iconOnlyNavigation = iconOnly,
            )
        }
    }
}
