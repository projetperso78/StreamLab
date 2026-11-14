package app.cleanroom.streamlab.services

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Country the user is currently in, without any location permission:
 * mobile network country (changes when crossing a border) → SIM country → device region.
 */
object RegionDetector {
    private val isoCountries: Set<String> = Locale.getISOCountries().toSet()

    fun pick(networkIso: String?, simIso: String?, localeCountry: String?): String? =
        listOf(networkIso, simIso, localeCountry)
            .map { it?.trim()?.uppercase(Locale.ROOT).orEmpty() }
            .firstOrNull { it in isoCountries }

    fun detect(context: Context): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val network = runCatching { telephony?.networkCountryIso }.getOrNull()
        val sim = runCatching { telephony?.simCountryIso }.getOrNull()
        val locales = context.resources.configuration.locales
        val region = if (locales.isEmpty) null else locales[0].country
        return pick(network, sim, region)
    }
}
