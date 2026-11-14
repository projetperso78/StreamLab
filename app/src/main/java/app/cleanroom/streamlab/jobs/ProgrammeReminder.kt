package app.cleanroom.streamlab.jobs

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.cleanroom.streamlab.MainActivity
import app.cleanroom.streamlab.R
import java.util.concurrent.TimeUnit

/** When a "programme starts soon" notification is due. Pure, unit-tested. */
object ReminderTiming {
    const val LEAD_MS = 5 * 60_000L

    /** Delay before notifying, or null when the programme has already started. */
    fun delayMs(startMs: Long, nowMs: Long, leadMs: Long = LEAD_MS): Long? =
        if (startMs <= nowMs) null else (startMs - leadMs - nowMs).coerceAtLeast(0L)

    fun key(channelId: Long, startMs: Long): String = "$channelId:$startMs"
}

/** TV guide reminders: a notification 5 minutes before a chosen programme starts. */
object ProgrammeReminder {
    private const val STORE = "streamlab_reminders"
    private const val KEYS = "keys"
    const val CHANNEL_ID = "programme_reminders"

    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun isSet(context: Context, channelId: Long, startMs: Long): Boolean =
        ReminderTiming.key(channelId, startMs) in prefs(context).getStringSet(KEYS, emptySet()).orEmpty()

    /** Turns the reminder on or off; returns true when it is now on. */
    fun toggle(context: Context, channelId: Long, startMs: Long, title: String, channelName: String, now: Long = System.currentTimeMillis()): Boolean {
        val key = ReminderTiming.key(channelId, startMs)
        val keys = prefs(context).getStringSet(KEYS, emptySet()).orEmpty().toMutableSet()
        val work = WorkManager.getInstance(context)
        if (key in keys) {
            keys -= key
            work.cancelUniqueWork("reminder-$key")
            prefs(context).edit().putStringSet(KEYS, keys).apply()
            return false
        }
        val delay = ReminderTiming.delayMs(startMs, now) ?: return false
        keys += key
        prefs(context).edit().putStringSet(KEYS, keys).apply()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("key" to key, "title" to title.take(120), "channel" to channelName.take(80)))
            .build()
        work.enqueueUniqueWork("reminder-$key", ExistingWorkPolicy.REPLACE, request)
        return true
    }

    internal fun forget(context: Context, key: String) {
        val keys = prefs(context).getStringSet(KEYS, emptySet()).orEmpty().toMutableSet()
        if (keys.remove(key)) prefs(context).edit().putStringSet(KEYS, keys).apply()
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString("key").orEmpty()
        ProgrammeReminder.forget(applicationContext, key)
        if (!ProgrammeReminder.canNotify(applicationContext)) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                ProgrammeReminder.CHANNEL_ID, applicationContext.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
        val open = PendingIntent.getActivity(
            applicationContext, key.hashCode(),
            Intent(applicationContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_INITIAL_SECTION, "Live"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, ProgrammeReminder.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_tv)
            .setContentTitle(applicationContext.getString(R.string.reminder_title, inputData.getString("title").orEmpty()))
            .setContentText(applicationContext.getString(R.string.reminder_text, inputData.getString("channel").orEmpty()))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        try {
            NotificationManagerCompat.from(applicationContext).notify(key.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post: nothing to show.
        }
        return Result.success()
    }
}
