package app.cleanroom.streamlab.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test fun ignoresAccentsCaseAndPunctuation() {
        assertTrue(TextMatch.matches("États-Unis", "Etats"))
        assertTrue(TextMatch.matches("États-Unis", "etats unis"))
        assertTrue(TextMatch.matches("Côte d’Ivoire", "cote d ivoire"))
        assertTrue(TextMatch.matches("13ème Rue", "13EME"))
        assertTrue(TextMatch.matches("المغرب", "المغرب"))
        assertTrue(TextMatch.matches("Anything", "  "))
        assertFalse(TextMatch.matches("France", "Allemagne"))
        assertFalse(TextMatch.matches(null, "x"))
    }
}
