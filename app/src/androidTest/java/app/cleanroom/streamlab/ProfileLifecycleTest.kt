package app.cleanroom.streamlab

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.data.ProfileType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileLifecycleTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun openSourcesFromHome() {
        rule.onNodeWithTag("nav-more").performClick()
        rule.onNodeWithText("Sources & listes").performClick()
    }


    @Test fun editAndDeleteProfileThroughVisibleUi() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        val original = "Lifecycle QA ${System.currentTimeMillis()}"
        val renamed = "$original edited"
        val id = runBlocking {
            app.repository.createProfile(original, ProfileType.MANUAL, PublicTestStream.URL)
        }
        try {
            rule.waitForIdle()
            rule.onNodeWithText("Gérer mes sources ▼").performScrollTo().performClick()
            rule.onNodeWithTag("profile-search").performTextReplacement(original)
            rule.waitUntil(10_000) { rule.onAllNodesWithText(original).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("profile-edit-$id").assertExists().performSemanticsAction(SemanticsActions.OnClick)
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Modifier la source").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Modifier la source").assertExists()
            val nameField = rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction().and(hasText("Nom")))[0]
            nameField.performTextClearance()
            nameField.performTextInput(renamed)
            rule.onNodeWithText("Enregistrer").performClick()
            rule.waitUntil(10_000) { runBlocking { app.repository.profiles.first().any { it.id == id && it.name == renamed } } }
            assertTrue(runBlocking { app.repository.profiles.first().any { it.id == id && it.name == renamed } })

            rule.onNodeWithTag("profile-search").performTextClearance()
            rule.onNodeWithTag("profile-search").performTextReplacement(renamed)
            rule.waitUntil(10_000) { rule.onAllNodesWithText(renamed).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("profile-delete-$id").assertExists().assertExists().performSemanticsAction(SemanticsActions.OnClick)
            rule.waitUntil(10_000) { runBlocking { app.repository.profiles.first().none { it.id == id } } }
        } finally {
            runBlocking { app.repository.profiles.first().firstOrNull { it.id == id }?.let { app.repository.deleteProfile(id) } }
        }
    }
}
