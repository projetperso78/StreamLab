package app.cleanroom.streamlab

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Application language: follow the device ("system") or force FR / EN / AR.
 * Main and passenger app IDs each keep their own choice locally, without network use.
 */
object StreamLabLocale {
    const val SYSTEM = "system"
    private const val STORE = "streamlab_language_preferences"
    private const val KEY = "selected_language"
    private const val FALLBACK = "en"
    private val supported = setOf("fr", "en", "ar")

    /** Explicit language codes only; anything else maps to French for legacy stored values. */
    fun normalize(code: String?): String =
        code?.lowercase(Locale.ROOT)?.takeIf { it in supported } ?: "fr"

    /** A stored or requested choice: "system", "fr", "en" or "ar". */
    fun normalizeSelection(code: String?): String {
        val lower = code?.lowercase(Locale.ROOT)
        return if (lower == null || lower == SYSTEM || lower !in supported) SYSTEM else lower
    }

    /** First supported language among the device languages, else English. */
    fun resolveSystem(deviceLanguages: List<String>): String =
        deviceLanguages.map { it.lowercase(Locale.ROOT) }.firstOrNull { it in supported } ?: FALLBACK

    fun isRtl(code: String): Boolean = normalize(code) == "ar"

    /** What the user picked in Settings (including "system"). */
    fun selection(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val appLocales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            val fromAndroid = appLocales?.takeIf { !it.isEmpty }?.get(0)?.language
            return if (fromAndroid in supported) normalize(fromAndroid) else SYSTEM
        }
        val stored = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, null)
        return normalizeSelection(stored)
    }

    /** The language actually displayed: "fr", "en" or "ar". */
    fun current(context: Context): String {
        val chosen = selection(context)
        return if (chosen == SYSTEM) resolveSystem(deviceLanguages()) else chosen
    }

    fun wrap(base: Context): Context {
        val language = Locale.forLanguageTag(current(base))
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(language)
            setLayoutDirection(language)
        }
        return base.createConfigurationContext(configuration)
    }

    fun select(activity: Activity, code: String) {
        val choice = normalizeSelection(code)
        if (selection(activity) == choice) return
        activity.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putString(KEY, choice).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (choice == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(choice)
        } else {
            activity.recreate()
        }
    }

    private fun deviceLanguages(): List<String> {
        val locales = Resources.getSystem().configuration.locales
        return (0 until locales.size()).map { locales[it].language }
    }
}
