package app.cleanroom.streamlab

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import app.cleanroom.streamlab.core.AlarmSchedule
import java.time.ZonedDateTime

/**
 * Radio alarm: at the chosen time the chosen radio (or the last one played) starts.
 * Uses AlarmManager.setAlarmClock, which needs the user-granted SCHEDULE_EXACT_ALARM on Android 12+
 * (StreamLab is not an alarm-clock app, so USE_EXACT_ALARM is not allowed on Google Play).
 */
object RadioAlarm {
    const val ACTION_FIRE = "app.cleanroom.streamlab.RADIO_ALARM"
    private const val STORE = "streamlab_alarm"
    private const val PENDING_WINDOW_MS = 60_000L
    private const val MIN_VOLUME_FRACTION = 0.4f

    fun load(context: Context): AlarmSchedule.Settings {
        val p = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        return AlarmSchedule.Settings(
            enabled = p.getBoolean("enabled", false),
            hour = p.getInt("hour", 7).coerceIn(0, 23),
            minute = p.getInt("minute", 0).coerceIn(0, 59),
            days = AlarmSchedule.decodeDays(p.getString("days", null)),
            mediaId = p.getString("media_id", null),
        )
    }

    /** Saves and (re)schedules. Returns the next ring, or null if off or not allowed. */
    fun save(context: Context, settings: AlarmSchedule.Settings): ZonedDateTime? {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", settings.enabled).putInt("hour", settings.hour).putInt("minute", settings.minute)
            .putString("days", AlarmSchedule.encodeDays(settings.days)).putString("media_id", settings.mediaId)
            .commit()
        return reschedule(context)
    }

    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun reschedule(context: Context, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = fireIntent(context)
        manager.cancel(operation)
        val next = AlarmSchedule.next(load(context), now) ?: return null
        if (!canSchedule(context)) return null
        val show = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_INITIAL_SECTION, "Alarm"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(next.toInstant().toEpochMilli(), show), operation)
        return next
    }

    private fun fireIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RadioAlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Rings now: makes the station audible and starts it through the normal media-button path. */
    fun ring(context: Context, now: Long = System.currentTimeMillis()) {
        val settings = load(context)
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString("pending_media_id", settings.mediaId ?: "")
            .putLong("pending_at", now).commit()
        raiseVolume(context)
        context.sendBroadcast(NowPlayingWidget.mediaKeyIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY))
    }

    fun hasPending(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val at = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getLong("pending_at", 0L)
        return at > 0L && now - at in 0..PENDING_WINDOW_MS
    }

    /**
     * Read once by playback resumption. Returns null when no alarm is ringing; an empty string means
     * "ring with the last played item".
     */
    fun takePending(context: Context, now: Long = System.currentTimeMillis()): String? {
        if (!hasPending(context, now)) return null
        val p = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val id = p.getString("pending_media_id", "")
        p.edit().remove("pending_media_id").putLong("pending_at", 0L).commit()
        return id
    }

    private fun raiseVolume(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val floor = (max * MIN_VOLUME_FRACTION).toInt().coerceAtLeast(1)
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) < floor) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, floor, 0) }
        }
    }
}

class RadioAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RadioAlarm.ACTION_FIRE -> {
                RadioAlarm.ring(context)
                val settings = RadioAlarm.load(context)
                // A one-time alarm switches itself off; a repeating one books the next day.
                if (settings.days.isEmpty()) RadioAlarm.save(context, settings.copy(enabled = false))
                else RadioAlarm.reschedule(context)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> RadioAlarm.reschedule(context)
        }
    }
}
