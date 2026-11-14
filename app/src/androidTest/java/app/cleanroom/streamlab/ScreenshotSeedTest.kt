package app.cleanroom.streamlab

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.data.ProfileType
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Demo content for the public screenshots: free test streams only (Big Buck Bunny, CC-BY Blender
 * Foundation), never a broadcaster's picture. Opt-in: run with -e seed 1.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotSeedTest {
    @Test fun seedDemoChannels() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seed") != null)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication
        runBlocking {
            listOf("Démo Cinéma", "Démo Nature", "Démo Animation", "Démo Découverte", "Démo Voyage", "Démo Famille")
                .forEach { app.repository.createProfile(it, ProfileType.MANUAL, PublicTestStream.URL + "?" + it.hashCode()) }
        }
        app.getSharedPreferences("streamlab_tips", 0).edit().putBoolean("radio", true).putBoolean("tv", true).putBoolean("player", true).commit()
    }
}
