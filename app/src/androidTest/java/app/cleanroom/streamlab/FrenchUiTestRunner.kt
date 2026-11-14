package app.cleanroom.streamlab

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.runner.AndroidJUnitRunner

/**
 * The UI regression suite asserts French copy. The app now follows the device language by
 * default, so pin the app (not the emulator) to French before any test starts. Tests that
 * exercise the "system" choice set it themselves and restore French afterwards.
 */
class FrenchUiTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        app.getSharedPreferences("streamlab_language_preferences", 0)
            .edit().putString("selected_language", "fr").commit()
        // UI tests start on the main screens; OnboardingTest resets this flag itself.
        app.getSharedPreferences("streamlab_onboarding", 0).edit().putBoolean("done", true).commit()
        // First-use guides would push screens down in every test; tests that check them reset this.
        app.getSharedPreferences("streamlab_tips", 0).edit().putBoolean("radio", true).putBoolean("tv", true).putBoolean("player", true).commit()
        // Never start a test locked in kids mode; KidsModeTest turns it on itself.
        app.getSharedPreferences("streamlab_kids", 0).edit().putBoolean("active", false).commit()
        if (Build.VERSION.SDK_INT >= 33) {
            app.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags("fr")
        }
        super.callApplicationOnCreate(app)
    }
}
