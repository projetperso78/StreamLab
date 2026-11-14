package app.cleanroom.streamlab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationLayoutTest {
    @Test fun compactNavigationProtectsSixTargetsAtSmallWidthAndLargeText() {
        assertTrue(MainNavigationLayout.iconOnly(320, 2f))
        assertFalse("a normal phone keeps the labels", MainNavigationLayout.iconOnly(400, 1f))
        assertTrue(MainNavigationLayout.iconOnly(320, 1f))
        assertTrue(MainNavigationLayout.iconOnly(540, 2f))
        assertFalse(MainNavigationLayout.iconOnly(600, 1f))
    }

    @Test fun backGoesUpOneLevelAndOnlyHomeLeavesTheApp() {
        org.junit.Assert.assertNull(MainNavigationLayout.backTarget("Apps"))
        org.junit.Assert.assertEquals("More", MainNavigationLayout.backTarget("Settings"))
        org.junit.Assert.assertEquals("More", MainNavigationLayout.backTarget("Alarm"))
        org.junit.Assert.assertEquals("Apps", MainNavigationLayout.backTarget("Radio"))
        org.junit.Assert.assertEquals("Apps", MainNavigationLayout.backTarget("More"))
        org.junit.Assert.assertEquals("Apps", MainNavigationLayout.backTarget("Drive"))
    }
}
