package app.cleanroom.streamlab

import android.app.Activity
import android.content.Context
import android.view.WindowManager

/** "Garder l'écran allumé": the TV picture stays lit on the car screen (and phone) while it plays. */
object CarScreen {
    private const val STORE = "streamlab_car"
    private const val KEEP_ON = "keep_screen_on"

    fun keepOn(context: Context): Boolean = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean(KEEP_ON, true)
    fun setKeepOn(context: Context, value: Boolean) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean(KEEP_ON, value).apply()
    }

    /** Applies the option to a player window: lit while [playing], normal otherwise. */
    fun apply(activity: Activity, playing: Boolean) {
        if (playing && keepOn(activity)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
