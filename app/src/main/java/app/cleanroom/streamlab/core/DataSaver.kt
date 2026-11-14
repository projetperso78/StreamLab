package app.cleanroom.streamlab.core

/**
 * "Économie de données": on mobile data (metered network) video is capped to 480p / 1.2 Mb/s,
 * about a third of HD; on Wi-Fi, or with the option off, there is no cap. Pure, unit-tested.
 */
object DataSaver {
    data class Limits(val maxVideoBitrate: Int, val maxVideoWidth: Int, val maxVideoHeight: Int)

    val NONE = Limits(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
    val MOBILE = Limits(1_200_000, 854, 480)

    fun limits(enabled: Boolean, metered: Boolean): Limits = if (enabled && metered) MOBILE else NONE
}
