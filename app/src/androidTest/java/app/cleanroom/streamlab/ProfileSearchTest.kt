package app.cleanroom.streamlab

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.data.ProfileType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileSearchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun openSourcesFromHome() {
        rule.onNodeWithTag("nav-more").performClick()
        rule.onNodeWithText("Sources & listes").performClick()
    }


    @Test fun profileSearchFiltersByNameAndShowsNoMatchState() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val suffix = System.currentTimeMillis().toString()
        val alpha = "Search Alpha $suffix"
        val beta = "Search Beta $suffix"
        val ids = runBlocking {
            listOf(
                app.repository.createProfile(alpha, ProfileType.MANUAL, PublicTestStream.URL),
                app.repository.createProfile(beta, ProfileType.MANUAL, PublicTestStream.URL),
            )
        }
        try {
            rule.waitUntil(10_000) {
                runBlocking { app.repository.profiles.first().count { it.id in ids } == 2 }
            }
            rule.onNodeWithText("Gérer mes sources ▼").performScrollTo().performClick()
            val search = rule.onNodeWithTag("profile-search")
            search.performTextInput("Alpha $suffix")
            rule.onNodeWithText(alpha).assertExists()
            rule.onNodeWithText(beta).assertDoesNotExist()

            search.performTextClearance()
            search.performTextInput("definitely-no-profile-$suffix")
            rule.onNodeWithText("Aucune source ne correspond à cette recherche.").assertExists()
        } finally {
            runBlocking { ids.forEach { app.repository.deleteProfile(it) } }
        }
    }
}
