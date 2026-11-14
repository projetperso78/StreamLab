package app.cleanroom.streamlab

import android.content.Context
import android.content.SharedPreferences

/** On by default: video is capped on mobile data (see core.DataSaver). */
object DataSaverSetting {
    private const val KEY = "data_saver"
    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("streamlab_data", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY, true)
    fun set(context: Context, value: Boolean) { prefs(context).edit().putBoolean(KEY, value).apply() }
}
