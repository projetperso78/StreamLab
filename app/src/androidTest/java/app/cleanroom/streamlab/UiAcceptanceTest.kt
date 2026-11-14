package app.cleanroom.streamlab

import androidx.compose.ui.test.assertIsSelected

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Account-free acceptance scenarios for controls visible to a regular user.
 * Uses only the isolated StreamLab QA emulator; restores user preferences.
 */
@RunWith(AndroidJUnit4::class)
class UiAcceptanceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun visibleThemeModesAndDensityPersistAcrossTabs() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val before = runBlocking { app.preferences.values.first() }
        try {
            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Réglages").performClick()
            rule.onNodeWithText("Apparence").assertExists()
            rule.onNodeWithText("Langue").assertExists()
            rule.onNodeWithText(rule.activity.getString(R.string.more_auto_title)).assertExists()
            for ((title, code) in listOf("Sombre" to "dark", "Clair" to "light", "Système" to "system")) {
                rule.onNodeWithText(title).performClick()
                rule.waitUntil(10_000) { runBlocking { app.preferences.values.first().theme == code } }
                assertEquals(code, runBlocking { app.preferences.values.first().theme })
            }
            val denseBefore = runBlocking { app.preferences.values.first().dense }
            rule.onNodeWithTag("settings-dense").performScrollTo().performClick()
            rule.waitUntil(10_000) { runBlocking { app.preferences.values.first().dense != denseBefore } }
            assertNotEquals(denseBefore, runBlocking { app.preferences.values.first().dense })
            rule.onNodeWithTag("nav-apps").performClick()
            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Réglages").performClick()
            assertEquals("system", runBlocking { app.preferences.values.first().theme })
            assertNotEquals(denseBefore, runBlocking { app.preferences.values.first().dense })
        } finally {
            runBlocking {
                app.preferences.setTheme(before.theme)
                app.preferences.setDense(before.dense)
            }
        }
    }

    @Test fun homeTilesNavigateToRealSections() {
        val tv = rule.activity.getString(R.string.pass_accessibility_title, rule.activity.getString(R.string.pass_tv))
        val radio = rule.activity.getString(R.string.pass_accessibility_title, rule.activity.getString(R.string.pass_radio))
        val favorites = rule.activity.getString(R.string.pass_accessibility_title, rule.activity.getString(R.string.pass_favorites))
        val videos = rule.activity.getString(R.string.pass_accessibility_title, rule.activity.getString(R.string.pass_videos))
        rule.onNodeWithTag("apps-grid").assertExists()
        rule.onNode(hasContentDescription(tv)).performClick()
        rule.onNodeWithTag("nav-live").assertExists()
        rule.onNodeWithTag("nav-apps").performClick()
        rule.onNode(hasContentDescription(radio)).performClick()
        rule.onNodeWithTag("nav-radio").assertIsSelected()
        rule.onNodeWithTag("nav-apps").performClick()
        rule.onNodeWithTag("apps-grid").performScrollToNode(hasContentDescription(favorites))
        rule.onNode(hasContentDescription(favorites)).performClick()
        rule.onNodeWithTag("nav-favorites").assertExists()
        rule.onNodeWithTag("nav-apps").performClick()
        rule.onNodeWithTag("apps-grid").performScrollToNode(hasContentDescription(videos))
        rule.onNode(hasContentDescription(videos)).performClick()
        rule.onNodeWithTag("nav-videos").assertIsSelected()
        rule.onNodeWithTag("nav-more").performClick()
        rule.onNodeWithText("Réglages").assertExists()
    }
}
