package app.cleanroom.streamlab

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.data.ProfileType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun topLevelNavigation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        runBlocking { app.repository.importDemoFixture("Navigation fixture") }
        rule.onNodeWithTag("apps-grid").assertExists()
        rule.onNodeWithTag("apps-grid").performScrollToNode(androidx.compose.ui.test.hasTestTag("passenger-card-drive"))
        rule.onNodeWithTag("passenger-card-drive").assertExists()
        rule.onNodeWithTag("nav-radio").performClick()
        rule.onNodeWithTag("nav-radio").assertIsSelected()
        rule.onNodeWithTag("nav-more").performClick()
        rule.onNodeWithText("Sources & listes").performClick()
        rule.onNodeWithTag("lib-add-source").assertExists()
        rule.onNodeWithText("Importer M3U").assertExists()
        rule.onNodeWithText("Ajouter une vidéo").assertExists()
        rule.onNodeWithTag("nav-live").performClick()
        rule.onNodeWithTag("tv-split").assertExists()
        rule.onNodeWithTag("nav-videos").performClick()
        rule.onNodeWithText("Ajouter une vidéo").assertExists()
        rule.onNodeWithTag("nav-more").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.more_auto_title)).assertExists()
        rule.onNodeWithText("Réglages").performClick()
        rule.onNodeWithText("Apparence").assertExists()
        rule.onNodeWithTag("nav-more").performClick()
        rule.onNodeWithText("Services web").performClick()
        rule.onNodeWithTag("apps-grid").performScrollToNode(androidx.compose.ui.test.hasText("Netflix"))
        rule.onNodeWithText("Netflix").assertExists()
    }

    @Test fun appShipsNoEmbeddedThirdPartyWebPlayer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activities = context.packageManager
            .getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_ACTIVITIES)
            .activities.orEmpty().map { it.name }
        assertTrue(activities.none { it.endsWith("BrowserActivity") })
    }

    @Test fun bundledFixturePersistsPlayableChannelAndLivePanelOpens() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val profileId = runBlocking { app.repository.importDemoFixture() }
        val persisted = runBlocking { app.repository.dao.channelsForProfile(profileId) }
        assertTrue(persisted.any { it.name == "StreamLab offline demo pattern" })
        rule.onNodeWithTag("nav-live").performClick()
        rule.onNodeWithText("Rechercher une chaîne").assertExists()
    }

    @Test fun castControlsRenderForPublicRemoteStream() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val name = "Cast UI ${System.nanoTime()}"
        val profileId = runBlocking {
            app.repository.createProfile(name, ProfileType.MANUAL, PublicTestStream.URL)
        }
        try {
            val row = runBlocking { app.repository.dao.channelsForProfile(profileId).single() }
            runBlocking { app.repository.toggleFavorite(row.id) }
            rule.onNodeWithTag("nav-favorites").performClick()
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(name).performClick()
            // The Cast button is the system device picker icon; emulators without an up-to-date
            // Play services have no Cast framework, and then the button is rightly hidden.
            val castReady = runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(app) }.isSuccess
            if (castReady) rule.onNode(androidx.compose.ui.test.hasContentDescription("Choisir un appareil Cast"), useUnmergedTree = true).assertExists()
            rule.onNodeWithText(name).assertExists()
        } finally {
            runBlocking { app.repository.deleteProfile(profileId) }
        }
    }
}
