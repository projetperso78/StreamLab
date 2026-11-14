package app.cleanroom.streamlab

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.AlarmSchedule
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.player.PlaybackService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioAlarmTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as StreamLabApplication

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    @Test(timeout = 180_000)
    fun alarmIsBookedAndRingingSwitchesToTheAlarmStation() {
        shell("appops set ${app.packageName} SCHEDULE_EXACT_ALARM allow")
        val first = runBlocking { app.repository.importDemoFixture("Alarm QA last") }
        val second = runBlocking { app.repository.importDemoFixture("Alarm QA wake") }
        val controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .buildAsync().get(15, TimeUnit.SECONDS)
        fun currentId(): String? { var id: String? = null; rule.runOnUiThread { id = controller.currentMediaItem?.mediaId }; return id }
        fun playing(): Boolean { var p = false; rule.runOnUiThread { p = controller.isPlaying }; return p }
        try {
            val lastId = runBlocking { app.repository.dao.channelsForProfile(first).first().id }
            val wakeId = runBlocking { app.repository.dao.channelsForProfile(second).first().id }
            runBlocking { app.repository.markPlayed(lastId); app.repository.toggleFavorite(wakeId) }

            // 1. The parent sets it up from More → Radio alarm, choosing the favourite station.
            rule.onNodeWithTag("nav-more").performClick()
            rule.onNodeWithText("Radio-réveil").performScrollTo().performClick()
            rule.onNodeWithTag("alarm-enabled").performClick()
            rule.onNodeWithTag("alarm-station-$wakeId").performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("alarm-next").assertIsDisplayed()
            val booked = app.getSystemService(AlarmManager::class.java).nextAlarmClock
            assertNotNull("alarm must be booked with AlarmManager", booked)
            assertEquals(OpaqueMediaId.channel(wakeId), RadioAlarm.load(app).mediaId)

            // 2. Something else is playing when the alarm fires.
            rule.runOnUiThread {
                controller.setMediaItem(MediaItem.Builder().setMediaId(OpaqueMediaId.channel(lastId)).build())
                controller.prepare(); controller.play()
            }
            rule.waitUntil(20_000) { currentId() == OpaqueMediaId.channel(lastId) && playing() }

            RadioAlarmReceiver().onReceive(app, Intent(app, RadioAlarmReceiver::class.java).setAction(RadioAlarm.ACTION_FIRE))
            rule.waitUntil(20_000) { currentId() == OpaqueMediaId.channel(wakeId) && playing() }
            assertFalse("a one-time alarm switches itself off after ringing", RadioAlarm.load(app).enabled)
        } finally {
            rule.runOnUiThread { controller.stop(); controller.clearMediaItems(); controller.release() }
            RadioAlarm.save(app, AlarmSchedule.Settings(enabled = false))
            runBlocking { app.repository.deleteProfile(first); app.repository.deleteProfile(second) }
            // Not revoked here on purpose: Android kills the app when SCHEDULE_EXACT_ALARM is revoked,
            // which would abort the instrumentation run. The grant only lives on the test emulator.
        }
    }
}
