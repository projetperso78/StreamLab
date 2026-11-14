package app.cleanroom.streamlab

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Car rules for StreamLab (AGENTS.md). The personal parked build ("StreamLab Passagers", never on
 * the Play Store) uses Android Auto's only parked category, games, so Android Auto shows it parked
 * and closes it when the car moves. The Play build never declares a parked app or a game.
 */
class CarManifestGuardTest {
    private fun manifest(sourceSet: String): String {
        val file = listOf(File("src/$sourceSet/AndroidManifest.xml"), File("app/src/$sourceSet/AndroidManifest.xml")).first { it.exists() }
        return file.readText()
    }

    @Test fun parkedBuildIsAParkedAppOnTheCarLauncher() {
        val parked = manifest("parkedDebug")
        assertTrue(parked.contains("android:appCategory=\"game\""))
        assertTrue(parked.contains("android.intent.category.CAR_LAUNCHER"))
    }

    @Test fun parkedBuildIsTheCompleteAppOnItsOwn() {
        val parked = manifest("parkedDebug")
        assertFalse("the full phone app must stay in the personal build", Regex("MainActivity\"\\s*tools:node=\"remove\"").containsMatchIn(parked))
        assertFalse("the player must stay in the personal build", Regex("PlaybackService\"\\s*tools:node=\"remove\"").containsMatchIn(parked))
        val car = listOf(File("src/parkedDebug/res/xml/automotive_app_desc.xml"), File("app/src/parkedDebug/res/xml/automotive_app_desc.xml")).first { it.exists() }.readText()
        assertTrue("radio and TV sound stay in Android Auto's media screen", car.contains("name=\"media\""))
    }

    @Test fun playBuildsAreNeverGames() {
        listOf("main", "debug", "release").forEach { set ->
            val text = runCatching { manifest(set) }.getOrNull() ?: return@forEach
            assertFalse("$set must not use the game category", text.contains("appCategory=\"game\""))
            assertFalse("$set must not use the game flag", text.contains("android:isGame=\"true\""))
        }
    }

    @Test fun storeBuildIsNotAParkedApp() {
        assertFalse("Play build stays audio-only in the car", manifest("main").contains("CAR_LAUNCHER"))
    }
}
