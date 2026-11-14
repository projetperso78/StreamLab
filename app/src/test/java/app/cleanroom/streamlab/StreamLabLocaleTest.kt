package app.cleanroom.streamlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLabLocaleTest {
    @Test fun onlyFrenchEnglishAndArabicAreAccepted() {
        assertEquals("fr", StreamLabLocale.normalize(null))
        assertEquals("fr", StreamLabLocale.normalize("de"))
        assertEquals("en", StreamLabLocale.normalize("en"))
        assertEquals("ar", StreamLabLocale.normalize("ar"))
        assertEquals("fr", StreamLabLocale.normalize("FR"))
    }

    @Test fun arabicAloneUsesRtl() {
        assertTrue(StreamLabLocale.isRtl("ar"))
        assertFalse(StreamLabLocale.isRtl("fr"))
        assertFalse(StreamLabLocale.isRtl("en"))
    }

    @Test fun systemSelectionIsTheDefaultAndRejectsUnknownCodes() {
        assertEquals("system", StreamLabLocale.normalizeSelection(null))
        assertEquals("system", StreamLabLocale.normalizeSelection("system"))
        assertEquals("system", StreamLabLocale.normalizeSelection("de"))
        assertEquals("ar", StreamLabLocale.normalizeSelection("AR"))
    }

    @Test fun systemLanguageResolvesToFirstSupportedDeviceLanguageElseEnglish() {
        assertEquals("ar", StreamLabLocale.resolveSystem(listOf("ar", "fr")))
        assertEquals("fr", StreamLabLocale.resolveSystem(listOf("de", "fr")))
        assertEquals("en", StreamLabLocale.resolveSystem(listOf("de", "es")))
        assertEquals("en", StreamLabLocale.resolveSystem(emptyList()))
    }
}
