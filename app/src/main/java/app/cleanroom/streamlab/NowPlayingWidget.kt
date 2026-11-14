package app.cleanroom.streamlab

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.session.MediaButtonReceiver

/** Pure widget logic, unit-tested: which media key a button sends and what the label shows. */
internal object WidgetState {
    fun toggleKey(playing: Boolean): Int = if (playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
    fun title(raw: CharSequence?, fallback: String): String =
        raw?.toString()?.removePrefix("📍")?.trim()?.take(80)?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * Home-screen widget: what is playing plus ⏮ ▶ ⏭. Buttons are plain media-button intents to
 * Media3's receiver, so ▶ also restarts the last radio when the app is closed (playback resumption).
 */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        private const val STORE = "streamlab_widget"
        private const val TITLE = "title"
        private const val PLAYING = "playing"

        /** Called by the playback service whenever the item or play state changes. */
        fun publish(context: Context, title: CharSequence?, playing: Boolean) {
            val label = WidgetState.title(title, context.getString(R.string.widget_idle))
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            if (prefs.getString(TITLE, null) == label && prefs.getBoolean(PLAYING, false) == playing) return
            prefs.edit().putString(TITLE, label).putBoolean(PLAYING, playing).apply()
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, label, playing))
        }

        internal fun state(context: Context): Pair<String, Boolean> {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            return (prefs.getString(TITLE, null) ?: context.getString(R.string.widget_idle)) to prefs.getBoolean(PLAYING, false)
        }

        private fun views(context: Context): RemoteViews = state(context).let { (title, playing) -> views(context, title, playing) }

        private fun views(context: Context, title: String, playing: Boolean) =
            RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                setTextViewText(R.id.widget_title, title)
                setImageViewResource(R.id.widget_toggle, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                setContentDescription(R.id.widget_toggle, context.getString(if (playing) R.string.widget_pause else R.string.widget_play))
                setOnClickPendingIntent(R.id.widget_previous, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_toggle, mediaKey(context, WidgetState.toggleKey(playing)))
                setOnClickPendingIntent(R.id.widget_next, mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT))
                setOnClickPendingIntent(R.id.widget_title, PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_INITIAL_SECTION, "Drive"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ))
            }

        internal fun mediaKeyIntent(context: Context, keyCode: Int): Intent =
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))

        private fun mediaKey(context: Context, keyCode: Int): PendingIntent =
            PendingIntent.getBroadcast(context, keyCode, mediaKeyIntent(context, keyCode), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
