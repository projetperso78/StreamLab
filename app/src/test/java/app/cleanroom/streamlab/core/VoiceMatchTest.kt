package app.cleanroom.streamlab.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMatchTest {
    @Test fun cleansWhatPeopleSay() {
        assertEquals("RTL", VoiceMatch.clean("mets RTL"))
        assertEquals("France Inter", VoiceMatch.clean("mets la radio France Inter sur StreamLab"))
        assertEquals("NRJ", VoiceMatch.clean("joue NRJ"))
        assertEquals("Fip", VoiceMatch.clean("Fip"))
        assertEquals("Skyrock", VoiceMatch.clean("play Skyrock on StreamLab"))
    }

    @Test fun exactNameBeatsLongerOnes() {
        val names = listOf("RTL2", "RTL", "Fun Radio")
        assertEquals(1, VoiceMatch.best(names, "mets RTL"))
    }

    @Test fun accentsAndCaseDoNotMatter() {
        assertEquals(0, VoiceMatch.best(listOf("Chérie FM", "Europe 1"), "cherie fm"))
    }

    @Test fun startsThenContains() {
        assertEquals(1, VoiceMatch.best(listOf("Radio Classique", "Fip Jazz", "Fip"), "fip jazz"))
        assertEquals(0, VoiceMatch.best(listOf("France Info", "Info Plus"), "france inf"))
        assertEquals(-1, VoiceMatch.best(listOf("RTL"), "zzz"))
        assertEquals(-1, VoiceMatch.best(listOf("RTL"), "mets"))
    }

    @Test fun radioInTheNameIsKept() {
        assertEquals("Radio Classique", VoiceMatch.clean("mets Radio Classique"))
        assertEquals("Radio Nova", VoiceMatch.clean("mets la radio Radio Nova"))
        assertEquals(1, VoiceMatch.best(listOf("Radio Etoile Plus", "Radio Etoile"), "Radio Etoile"))
    }
}
