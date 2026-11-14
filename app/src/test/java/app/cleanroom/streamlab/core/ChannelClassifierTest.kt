package app.cleanroom.streamlab.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelClassifierTest {
    @Test fun explicitPlaylistAttributesWin() {
        val origin = ChannelClassifier.classify(tvgCountry = "MA", tvgLanguage = "French", tvgId = "x.fr", name = "UK: Test")
        assertEquals("MA", origin.country)
        assertEquals("fr", origin.language)
    }

    @Test fun tvgIdSuffixGivesTheCountry() {
        assertEquals("FR", ChannelClassifier.classify(tvgId = "France24French.fr@HD").country)
        assertEquals("GB", ChannelClassifier.classify(tvgId = "BBCOne.uk").country)
        assertNull(ChannelClassifier.classify(tvgId = "Something.com").country)
    }

    @Test fun commonNamePrefixes() {
        assertEquals("FR", ChannelClassifier.classify(name = "FR: TF1").country)
        assertEquals("GB", ChannelClassifier.classify(name = "UK - BBC One").country)
        assertEquals("MA", ChannelClassifier.classify(name = "|MAR| 2M Monde").country)
        assertEquals("DE", ChannelClassifier.classify(group = "DE | Sport").country)
    }

    @Test fun arPrefixMeansArabicNotArgentina() {
        val origin = ChannelClassifier.classify(name = "AR: MBC 1")
        assertNull(origin.country)
        assertEquals("ar", origin.language)
    }

    @Test fun countryNamesInGroupsInFrenchOrEnglish() {
        assertEquals("MA", ChannelClassifier.classify(group = "Maroc").country)
        assertEquals("DZ", ChannelClassifier.classify(group = "Algeria TV").country)
        assertEquals("BE", ChannelClassifier.classify(group = "Chaînes Belgique").country)
        assertEquals("AE", ChannelClassifier.classify(group = "Émirats arabes unis").country)
    }

    @Test fun languageWordsInGroups() {
        assertEquals("ar", ChannelClassifier.classify(group = "Arabic News").language)
        assertEquals("en", ChannelClassifier.classify(group = "Chaînes en anglais").language)
    }

    @Test fun unknownStaysUnknown() {
        val origin = ChannelClassifier.classify(name = "Ma chaîne", group = "Other")
        assertNull(origin.country)
        assertNull(origin.language)
    }

    // Real iptv-org France entries (13 Nov 2026): the feed after "@" is where it is broadcast.
    @Test fun broadcastFeedBeatsOwnerCountry() {
        assertEquals("FR", ChannelClassifier.classify(tvgId = "CineActionbyPlutoTV.de@FR").country)
        assertEquals("FR", ChannelClassifier.classify(tvgId = "AlerteaMalibu.us@France").country)
        assertEquals("FR", ChannelClassifier.classify(tvgId = "6ter.fr@SD").country)
    }

    @Test fun countryPlaylistAddressFilesItsChannels() {
        assertEquals("FR", ChannelClassifier.countryFromPlaylistUrl("https://iptv-org.github.io/iptv/countries/fr.m3u"))
        assertEquals(null, ChannelClassifier.countryFromPlaylistUrl("https://example.com/list.m3u"))
        assertEquals("FR", ChannelClassifier.classify(tvgId = "Africa24English.xx", playlistCountry = "FR").country)
    }
}
