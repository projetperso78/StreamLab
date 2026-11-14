package app.cleanroom.streamlab

import android.appwidget.AppWidgetManager
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamLabApplication

    @Test fun widgetIsOfferedToTheLauncher() {
        val providers = AppWidgetManager.getInstance(app).getInstalledProvidersForPackage(app.packageName, null)
        assertTrue(providers.any { it.provider.className == NowPlayingWidget::class.java.name })
    }

    @Test fun widgetPlayButtonResumesLastItemAndWidgetFollowsPlayState() {
        val profile = runBlocking { app.repository.importDemoFixture("Widget QA") }
        try {
            val row = runBlocking { app.repository.dao.channelsForProfile(profile).first() }
            runBlocking { app.repository.markPlayed(row.id) }
            app.sendBroadcast(NowPlayingWidget.mediaKeyIntent(app, KeyEvent.KEYCODE_MEDIA_PAUSE))
            rule.waitUntil(10_000) { !NowPlayingWidget.state(app).second }

            // Exactly what the widget's ▶ button sends.
            app.sendBroadcast(NowPlayingWidget.mediaKeyIntent(app, KeyEvent.KEYCODE_MEDIA_PLAY))
            rule.waitUntil(20_000) { NowPlayingWidget.state(app).second }
            assertTrue("widget shows the resumed item", NowPlayingWidget.state(app).first.isNotBlank())

            app.sendBroadcast(NowPlayingWidget.mediaKeyIntent(app, KeyEvent.KEYCODE_MEDIA_PAUSE))
            rule.waitUntil(10_000) { !NowPlayingWidget.state(app).second }
        } finally {
            app.sendBroadcast(NowPlayingWidget.mediaKeyIntent(app, KeyEvent.KEYCODE_MEDIA_PAUSE))
            runBlocking { app.repository.deleteProfile(profile) }
        }
    }
}
