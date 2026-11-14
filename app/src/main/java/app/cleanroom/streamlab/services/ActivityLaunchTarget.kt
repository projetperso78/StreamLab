package app.cleanroom.streamlab.services

import android.app.ActivityOptions
import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Display

/** Prevents an in-car app handoff from silently landing on a different (driver-visible) display. */
internal object ActivityLaunchTarget {
    @Suppress("DEPRECATION")
    fun displayIdFor(context: Context): Int? {
        val activity = context as? Activity ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.displayId
        } else {
            activity.windowManager.defaultDisplay.displayId
        }
    }

    fun optionsForDisplay(displayId: Int?): ActivityOptions? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || displayId == null || displayId == Display.INVALID_DISPLAY) {
            return null
        }
        return ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
    }
}
