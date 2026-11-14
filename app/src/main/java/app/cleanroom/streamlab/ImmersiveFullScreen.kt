package app.cleanroom.streamlab

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

/**
 * Full screen like a video app: landscape, no system bars. Leaving it returns to the orientation
 * the user came from, then hands rotation back to the phone after a moment.
 */
@Composable
fun ImmersiveFullScreen(fullScreen: Boolean) {
    val activity = androidx.activity.compose.LocalActivity.current ?: return
    val portraitNow = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    var cameFromPortrait by rememberSaveable { mutableStateOf(true) }
    var wasFull by rememberSaveable { mutableStateOf(false) }
    // Set only when leaving full screen: rotation is handed back once, never on a normal screen.
    var releasePending by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(fullScreen) {
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullScreen) {
            if (!wasFull) cameFromPortrait = portraitNow
            wasFull = true
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else if (wasFull) {
            wasFull = false
            releasePending = true
            activity.requestedOrientation = if (cameFromPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullScreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                bars.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(releasePending) {
        if (releasePending) {
            delay(2_000)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            releasePending = false
        }
    }
}
