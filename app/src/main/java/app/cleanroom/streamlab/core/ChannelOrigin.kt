package app.cleanroom.streamlab.core

import java.util.Locale

/** Display helpers for channel/radio countries and languages. */
object ChannelOrigin {
    fun flag(code: String): String = code.uppercase(Locale.ROOT).take(2).map { char ->
        String(Character.toChars(0x1F1E6 + (char.code - 'A'.code).coerceIn(0, 25)))
    }.joinToString("")

    fun countryName(code: String, display: Locale = Locale.getDefault()): String = runCatching {
        Locale.Builder().setRegion(code.uppercase(Locale.ROOT)).build().getDisplayCountry(display)
            .ifBlank { code.uppercase(Locale.ROOT) }
    }.getOrDefault(code.uppercase(Locale.ROOT))

    /** Each language in its own script, so a speaker recognises it whatever the UI language. */
    fun languageName(code: String): String = runCatching {
        val locale = Locale.forLanguageTag(code)
        locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }.ifBlank { code }
    }.getOrDefault(code)
}
