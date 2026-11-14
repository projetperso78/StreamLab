package app.cleanroom.streamlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaLayoutTest {
    @Test fun phoneDoesNotCrushTitlesIntoNarrowCards() {
        val narrow = CinemaLayout.forViewport(widthDp = 320, fontScale = 1f)
        assertFalse(narrow.splitHero)
        assertTrue(narrow.compactHero)
        assertTrue(CinemaLayout.forViewport(widthDp = 360, fontScale = 1f).compactHero)
        assertEquals(1, CinemaLayout.forViewport(widthDp = 360, fontScale = 1f).serviceColumns)
        assertEquals(1, CinemaLayout.forViewport(widthDp = 360, fontScale = 2f).serviceColumns)
        assertTrue(CinemaLayout.forViewport(widthDp = 320, fontScale = 1.5f).compactHero)
        assertTrue(CinemaLayout.forViewport(widthDp = 320, fontScale = 1.5f).iconOnlyNavigation)
        assertEquals(5, CinemaLayout.forViewport(widthDp = 320, fontScale = 1.5f).navigationColumns)
        assertFalse(CinemaLayout.forViewport(widthDp = 393, fontScale = 1.5f).compactHero)
        assertEquals(1, narrow.featureColumns)
        assertEquals(1, narrow.serviceColumns)
        assertEquals(2, CinemaLayout.forViewport(393, 1f).serviceColumns)
    }

    @Test fun rearScreenUsesSeparateHeroAndFillsRemainingArea() {
        val wide = CinemaLayout.forViewport(widthDp = 1280, fontScale = 1f, heightDp = 720)
        assertTrue(wide.splitHero)
        assertEquals(2, wide.featureColumns)
        assertEquals(4, wide.serviceColumns)
        val typicalEmulatorLandscape = CinemaLayout.forViewport(853, 1f, heightDp = 480)
        assertTrue(typicalEmulatorLandscape.splitHero)
        assertEquals(2, typicalEmulatorLandscape.featureColumns)
        assertEquals(3, typicalEmulatorLandscape.serviceColumns)
        assertFalse(CinemaLayout.forViewport(853, 1f, heightDp = 1100).splitHero)
    }

    @Test fun accessibilityFontScaleForcesWiderButtons() {
        val huge = CinemaLayout.forViewport(widthDp = 393, fontScale = 2f)
        assertFalse(huge.splitHero)
        assertEquals(1, huge.featureColumns)
        assertEquals(1, huge.serviceColumns)
        assertEquals(5, huge.navigationColumns)
        assertTrue(huge.iconOnlyNavigation)
        assertEquals(5, CinemaLayout.forViewport(393, 1f).navigationColumns)
        assertEquals(5, CinemaLayout.forViewport(853, 1f, 480).navigationColumns)
        assertTrue(CinemaLayout.navigationCellWidthDp(853, 5) * 5 + 4 * 4 <= 853 - 12)
        assertTrue(CinemaLayout.navigationCellWidthDp(320, 3) * 3 + 4 * 2 <= 320 - 12)
        assertTrue(CinemaLayout.navigationCellWidthDp(360, 5) * 5 + 4 * 4 <= 360 - 12)
        assertTrue(CinemaLayout.navigationCellWidthDp(320, 5) >= 48)
        assertFalse(CinemaLayout.forViewport(1280, fontScale = 2f).splitHero)
    }
}
