package app.cleanroom.streamlab

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CinemaPremiumNavigationTest {
    @get:Rule(order = 0) val retry = RetryOnEmulatorRotation()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<CarServicesActivity>()

    @Test fun phoneBottomNavigationKeepsAllFiveDestinationsOnOneRow() {
        val ids = listOf("home", "live", "radio", "favorites", "more")
        val tops = ids.map { id ->
            rule.onNodeWithTag("passenger-nav-$id", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue("Phone navigation destinations are split across rows: $tops", tops.maxOrNull()!! - tops.minOrNull()!! < 1f)
    }

    @Test fun offersRealHomeShortcutsAndAnAccessibleSettingsRoute() {
        rule.onNodeWithTag("cinema-passenger-home").assertExists()
        rule.onNodeWithTag("cinema-navigation").assertExists()
        rule.onNodeWithTag("passenger-card-live").assertExists()
        rule.onNodeWithTag("passenger-grid", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("passenger-card-radio"))
        rule.onNodeWithTag("passenger-card-radio").assertExists()
        rule.onNodeWithTag("passenger-nav-more").assertExists()
    }

    @Test fun passengerHomeShowsTheSafetyNotice() {
        rule.onNodeWithTag("passenger-safety-notice").assertExists().assertIsDisplayed()
        rule.onNodeWithTag("passenger-safety-body").assertTextEquals(
            rule.activity.getString(R.string.pass_safety_body),
        ).assertIsDisplayed()
    }

    @Test fun dpadMovesFocusAcrossPassengerShortcuts() {
        val home = rule.onNodeWithTag("passenger-nav-home")
        home.performSemanticsAction(SemanticsActions.RequestFocus)
        home.assertIsFocused()

        home.performKeyInput { pressKey(Key.DirectionRight) }

        rule.onNodeWithTag("passenger-nav-live").assertIsFocused()
    }

    @Test(timeout = 120_000) fun wideServiceCardsStayInOneDpadNavigableRow() {
        val previousOrientation = rule.activity.requestedOrientation
        try {
            rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            rule.waitForIdle()
            rule.onNodeWithTag("passenger-grid", useUnmergedTree = true)
                .performScrollToNode(hasTestTag("passenger-service-carousel"))
            rule.onNodeWithTag("passenger-service-carousel").assertExists().assertIsDisplayed()

            var current = rule.onNodeWithTag("passenger-service-youtube")
            current.performSemanticsAction(SemanticsActions.RequestFocus)
            current.assertIsFocused()
            listOf("netflix", "prime_video", "disney_plus", "emby", "jellyfin", "plex", "twitch")
                .forEach { nextId ->
                    current.performKeyInput { pressKey(Key.DirectionRight) }
                    current = rule.onNodeWithTag("passenger-service-$nextId")
                    current.assertExists().assertIsFocused()
                }
        } finally {
            rule.activity.requestedOrientation = previousOrientation
        }
    }

    @Test(timeout = 120_000) fun wideHomeKeepsLibraryHeadingAboveFixedNavigation() {
        val previousOrientation = rule.activity.requestedOrientation
        try {
            rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            rule.waitForIdle()
            rule.onNodeWithTag("passenger-grid", useUnmergedTree = true)
                .performScrollToNode(hasTestTag("cinema-section-03"))
            rule.waitForIdle()
            val library = rule.onNodeWithTag("cinema-section-03", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val navigationTop = rule.onNodeWithTag("cinema-navigation", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.top
            val minimumBreathingRoom = with(rule.activity.resources.displayMetrics) {
                8f * density
            }
            assertTrue(
                "Library heading has no breathing room above fixed navigation: library=$library navigationTop=$navigationTop",
                library.bottom + minimumBreathingRoom <= navigationTop,
            )
        } finally {
            rule.activity.requestedOrientation = previousOrientation
        }
    }

    @Test(timeout = 120_000) fun serviceCardsRenderWithoutBundledThirdPartyLogos() {
        val previousOrientation = rule.activity.requestedOrientation
        try {
            rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            rule.waitForIdle()
            listOf("netflix", "twitch", "plex", "prime_video", "disney_plus").forEach { id ->
                rule.onNodeWithTag("passenger-grid", useUnmergedTree = true)
                    .performScrollToNode(hasTestTag("passenger-service-carousel"))
                rule.onNodeWithTag("passenger-service-carousel", useUnmergedTree = true)
                    .performScrollToNode(hasTestTag("passenger-service-$id"))
                rule.onNodeWithTag("service-brand-$id", useUnmergedTree = true).assertExists()
            }
            listOf("prime_video", "disney_plus").forEach { id ->
                rule.onNodeWithTag("passenger-grid", useUnmergedTree = true)
                    .performScrollToNode(hasTestTag("passenger-service-carousel"))
                rule.onNodeWithTag("passenger-service-carousel", useUnmergedTree = true)
                    .performScrollToNode(hasTestTag("passenger-service-$id"))
                rule.onNodeWithTag("service-brand-image-$id", useUnmergedTree = true).assertDoesNotExist()
            }
        } finally {
            rule.activity.requestedOrientation = previousOrientation
        }
    }
}
