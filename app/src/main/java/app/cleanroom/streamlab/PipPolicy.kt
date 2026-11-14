package app.cleanroom.streamlab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Leaving the app while a TV channel or a video is on screen keeps it playing in a small
 * window (picture in picture). Screens showing video register here while they are visible.
 */
object PipPolicy {
    /** Number of visible screens currently showing video. */
    val videoScreens = mutableIntStateOf(0)
    val active: Boolean get() = videoScreens.intValue > 0
    /** True while the app is shown as the small window: screens must not pause what plays. */
    @Volatile var inPip = false
    /** Last TV channel on screen, so coming back from the small window shows the same one. */
    @Volatile var lastTvChannelId: Long? = null
}

/** Marks the calling screen as "video on screen" while [showingVideo] is true. */
@Composable
fun RegisterVideoForPip(showingVideo: Boolean) {
    DisposableEffect(showingVideo) {
        if (showingVideo) PipPolicy.videoScreens.intValue++
        onDispose { if (showingVideo) PipPolicy.videoScreens.intValue = (PipPolicy.videoScreens.intValue - 1).coerceAtLeast(0) }
    }
}

/** What the small window shows: only the picture. */
@Composable
fun PipVideo(controller: MediaController?) {
    AndroidView(
        factory = { context -> PlayerView(context).apply { useController = false; player = controller; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
        update = { it.player = controller },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
}
