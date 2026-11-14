package app.cleanroom.streamlab

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

class PassengerActivity : ComponentActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private lateinit var playPause: Button
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = refreshStatus()
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshStatus()
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = refreshStatus()
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = TextView(this).apply {
            text = "StreamLab Passagers"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        status = TextView(this).apply {
            text = "Connexion au lecteur StreamLab…"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        playerView = PlayerView(this).apply {
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
        }
        playPause = Button(this).apply {
            text = "Lecture"
            isEnabled = false
            setOnClickListener {
                controller?.let { current ->
                    if (current.isPlaying) current.pause() else current.play()
                }
            }
        }
        val openMain = Button(this).apply {
            text = "Ouvrir StreamLab"
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage(MAIN_PACKAGE)
                if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else status.text = "Installe ou lance d’abord l’application StreamLab principale."
            }
        }
        val fit = Button(this).apply {
            text = "Zoom"
            setOnClickListener {
                val next = if (playerView.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                playerView.resizeMode = next
                text = if (next == AspectRatioFrameLayout.RESIZE_MODE_FIT) "Zoom" else "Ajuster"
            }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(playPause, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(openMain, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(fit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(playerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
        // Car screens put Android Auto's rail and bars over the app: keep everything inside the
        // safe area so names and buttons are never cut (car app quality AR-1).
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        connectToMainPlaybackService()
    }

    private fun connectToMainPlaybackService() {
        val component = ComponentName(MAIN_PACKAGE, PLAYBACK_SERVICE_CLASS)
        val future = MediaController.Builder(this, SessionToken(this, component)).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }
                .onSuccess { mediaController ->
                    controller = mediaController
                    playerView.player = mediaController
                    mediaController.addListener(listener)
                    playPause.isEnabled = true
                    refreshStatus()
                }
                .onFailure {
                    status.text = "Lecteur StreamLab principal indisponible. Ouvre StreamLab puis relance une TV/M3U."
                }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun refreshStatus() {
        val current = controller ?: return
        val title = current.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Aucun flux sélectionné"
        val state = when (current.playbackState) {
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_READY -> if (current.isPlaying) "Lecture" else "Prêt"
            Player.STATE_ENDED -> "Terminé"
            else -> "Inactif"
        }
        val groups = current.currentTracks.groups
        val audio = groups.any { it.type == C.TRACK_TYPE_AUDIO }
        val video = groups.any { it.type == C.TRACK_TYPE_VIDEO }
        status.text = "$title · $state · Audio: ${if (audio) "oui" else "non"} · Vidéo: ${if (video) "oui" else "non"}"
        playPause.text = if (current.isPlaying) "Pause" else "Lecture"
    }

    override fun onDestroy() {
        controller?.removeListener(listener)
        playerView.player = null
        controller?.release()
        controller = null
        controllerFuture?.cancel(false)
        controllerFuture = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val MAIN_PACKAGE = "app.cleanroom.streamlab"
        private const val PLAYBACK_SERVICE_CLASS = "app.cleanroom.streamlab.player.PlaybackService"
    }
}
