package app.cleanroom.streamlab

import android.app.LocaleManager
import android.os.Build
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppsServicesLocaleTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Android applies an app-locale change asynchronously by recreating the activity. Wait until
     * French is really back, otherwise the recreation lands in the next test (seen as a hang in
     * CinemaPremiumNavigationTest's rotation tests).
     */
    private fun restoreFrench() {
        rule.runOnUiThread { StreamLabLocale.select(rule.activity, "fr") }
        rule.waitUntil(15_000) {
            runCatching { rule.activity.resources.configuration.locales[0].language == "fr" }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    @Test fun mainServicesScreenUsesTheSelectedArabicLanguage() {
        try {
            rule.runOnUiThread { StreamLabLocale.select(rule.activity, "ar") }
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText("سينماك ترافقك").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("سينماك ترافقك").assertExists()
        } finally {
            restoreFrench()
        }
    }

    @Test fun mainSettingsCanSelectTheDeviceSystemLanguage() {
        try {
            rule.onNodeWithTag("language-system").assertDoesNotExist()
            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Réglages").performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("language-system").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("language-system").performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("language-system").assertIsSelected()
            assertEquals(
                "system",
                rule.activity.getSharedPreferences("streamlab_language_preferences", 0)
                    .getString("selected_language", null),
            )
            if (Build.VERSION.SDK_INT >= 33) {
                rule.runOnIdle {
                    assertTrue(
                        rule.activity.getSystemService(LocaleManager::class.java)
                            .applicationLocales.isEmpty,
                    )
                }
            }
        } finally {
            restoreFrench()
        }
    }

    @Test fun mobileLibraryLabelsFollowTheLanguageSelectedInSettings() {
        try {
            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Réglages").performClick()
            rule.onNodeWithTag("language-en").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText("Language").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Sources and lists").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText("Add content").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("lib-add-source").assertExists()
            rule.onNodeWithText("Add content").assertExists()

            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Settings").performClick()
            rule.onNodeWithTag("language-ar").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText("اللغة").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("المصادر والقوائم").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText("إضافة محتوى").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("lib-add-source").assertExists()
            rule.onNodeWithText("إضافة محتوى").assertExists()
        } finally {
            restoreFrench()
        }
    }
}
