package app.cleanroom.streamlab.player

import app.cleanroom.streamlab.data.CatalogChannelRow
import app.cleanroom.streamlab.data.ProfileEntity
import app.cleanroom.streamlab.data.ProfileType
import org.junit.Assert.*
import org.junit.Test

class AutoLibraryTest {
    private val tvProfile = ProfileEntity(id = 7, name = "TV France - Direct", type = ProfileType.MANUAL.name)
    private val radioProfile = ProfileEntity(id = 8, name = "Radio · FR", type = ProfileType.MANUAL.name)
    private val videoProfile = ProfileEntity(id = 9, name = "Film local", type = ProfileType.LOCAL_MEDIA.name)
    private val tv = CatalogChannelRow(42, 7, "BFM TV", "France", null, null, null, true, 123L)
    private val radio = CatalogChannelRow(43, 8, "France Info", "Radio FR", null, null, null, false, 120L)
    private val video = CatalogChannelRow(44, 9, "Film.mp4", "Vidéos", null, null, null, false, 119L)

    @Test fun freshInstallOpensOnRadioWithoutAnEmptyTvTab() {
        val library = AutoLibrary(listOf(radioProfile), listOf(radio))
        assertEquals(listOf("Radio", "Favoris", "Récents", "Sources"), library.children(AutoLibrary.ROOT).map { it.title })
        assertEquals("TV", library.item(AutoLibrary.TV)?.title)
    }

    @Test fun rootSeparatesTvRadioCountriesAndCarSafeAudio() {
        val library = AutoLibrary(listOf(tvProfile, radioProfile, videoProfile), listOf(tv, radio, video))
        assertEquals(AutoLibrary.ROOT, library.root().id)
        assertEquals(listOf("Radio", "TV", "Favoris", "Récents", "Sources"), library.children(AutoLibrary.ROOT).map { it.title })
        assertEquals("BFM TV", library.children(AutoLibrary.TV).single().title)
        val country = library.children(AutoLibrary.RADIO).single()
        assertEquals(AutoLibrary.radioCountryId("FR"), country.id)
        assertTrue(country.browsable)
        assertFalse(country.playable)
        assertTrue(country.title.contains("France", ignoreCase = true) || country.title.contains("FR"))
        assertEquals("France Info", library.children(country.id).single().title)
        assertEquals("BFM TV", library.children(AutoLibrary.FAVORITES).single().title)
        assertFalse(library.children(AutoLibrary.RECENT).any { it.title == "Film.mp4" })
        assertNull(library.channelId("channel:44"))
        assertTrue(library.children(AutoLibrary.ROOT).all { it.browsable && !it.playable })
    }

    @Test fun profilesGroupsCountryNodesAndSensitiveNamesFailClosed() {
        val library = AutoLibrary(listOf(tvProfile, radioProfile), listOf(tv, radio))
        assertEquals("France", library.children("profile:7").single().title)
        assertEquals("BFM TV", library.children("group:7:0").single().title)
        assertEquals(AutoLibrary.radioCountryId("FR"), library.item(AutoLibrary.radioCountryId("FR"))?.id)
        assertTrue(library.children("unknown").isEmpty())

        val sensitive = tv.copy(name = "https://example.com/live?token=private", category = "password=private")
        val safe = AutoLibrary(listOf(tvProfile.copy(name = "user=private")), listOf(sensitive))
        val visible = (safe.children(AutoLibrary.ROOT) + safe.children(AutoLibrary.PROFILES) +
            safe.children(AutoLibrary.FAVORITES)).joinToString { it.title }
        assertFalse(visible.contains("private"))
        assertFalse(visible.contains("example.com"))
        assertNull(safe.channelId("channel:999"))
        assertEquals(42L, safe.channelId("channel:42"))
    }

    @Test fun radioCategoriesAreExcludedFromTvEvenInNonRadioProfiles() {
        val importedRadio = CatalogChannelRow(45, 7, "Exa FM", "Radio AD", null, null, null, false, null)
        val library = AutoLibrary(listOf(tvProfile, radioProfile), listOf(tv, radio, importedRadio))

        assertTrue(library.isRadioChannel(importedRadio))
        assertTrue(library.isRadioChannel(radio))
        assertFalse(library.isRadioChannel(tv))
        assertEquals(listOf("BFM TV"), library.children(AutoLibrary.TV).map { it.title })
        assertEquals("Exa FM", library.children(AutoLibrary.radioCountryId("AD")).single().title)
    }

    @Test fun tvIsGroupedByCountryWhenChannelsComeFromSeveralCountries() {
        val fr = CatalogChannelRow(50, 7, "TF1", "FR", null, null, null, false, null, country = "FR")
        val ma = CatalogChannelRow(51, 7, "2M", "MA", null, null, null, false, null, country = "MA", language = "ar")
        val other = CatalogChannelRow(52, 7, "Ma chaîne", "Divers", null, null, null, false, null)
        val library = AutoLibrary(listOf(tvProfile), listOf(fr, ma, other))
        val folders = library.children(AutoLibrary.TV)
        assertEquals(listOf("tv-country:FR", "tv-country:MA", "tv-country:other"), folders.map { it.id })
        assertTrue(folders.all { it.browsable && !it.playable })
        assertEquals("2M", library.children("tv-country:MA").single().title)
        assertEquals("Ma chaîne", library.children("tv-country:other").single().title)
        assertEquals("tv-country:other", folders.last().id)
    }

    @Test fun singleCountryKeepsAFlatTvList() {
        val library = AutoLibrary(listOf(tvProfile), listOf(tv))
        assertEquals("BFM TV", library.children(AutoLibrary.TV).single().title)
    }
}
