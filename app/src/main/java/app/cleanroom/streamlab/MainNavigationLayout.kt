package app.cleanroom.streamlab

internal object MainNavigationLayout {
    /** Labels stay visible on normal phones; only tiny screens or very large text drop them. */
    fun iconOnly(widthDp: Int, fontScale: Float): Boolean =
        widthDp < 340 || (fontScale >= 1.5f && widthDp < 600)

    /** Where system Back goes from a section; null on Home, where Back leaves the app. */
    fun backTarget(section: String): String? = when (section) {
        "Apps" -> null
        "Library", "Guide", "Settings", "Share", "Alarm" -> "More"
        else -> "Apps"
    }
}
