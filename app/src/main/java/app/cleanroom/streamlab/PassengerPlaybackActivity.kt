package app.cleanroom.streamlab

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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
import app.cleanroom.streamlab.player.PlaybackService

/** Video surface for a passenger/rear display. It connects to the signed main StreamLab app. */
class PassengerPlaybackActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var stateView: TextView
    private lateinit var playPause: Button

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = renderState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = renderState()
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = renderState()
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = renderState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        connectToMainPlayer()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(18, 14, 18, 14)
        }
        titleView = TextView(this).apply {
            text = "StreamLab Passagers"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(4, 4, 4, 8)
        }
        stateView = TextView(this).apply {
            text = "Connexion au lecteur StreamLab…"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(4, 0, 4, 10)
        }
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        playPause = Button(this).apply {
            text = "Lecture"
            isEnabled = false
            setOnClickListener {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }
        }
        controls.addView(playPause, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply {
            text = "TV / Radio"
            setOnClickListener {
                startActivity(
                    Intent().setClassName(MAIN_APP_PACKAGE, MainActivity::class.java.name)
                        .putExtra(MainActivity.EXTRA_INITIAL_SECTION, "Live")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply {
            text = "Services"
            setOnClickListener { startActivity(Intent(this@PassengerPlaybackActivity, CarServicesActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(titleView)
        root.addView(stateView)
        root.addView(playerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(controls)
        setContentView(root)
    }

    private fun connectToMainPlayer() {
        val component = ComponentName(MAIN_APP_PACKAGE, PlaybackService::class.java.name)
        val future = MediaController.Builder(applicationContext, SessionToken(applicationContext, component)).buildAsync()
        future.addListener({
            runCatching { future.get() }
                .onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(listener)
                    playerView.player = mediaController
                    renderState()
                }
                .onFailure { failure ->
                    stateView.text = "Lecteur principal indisponible : ${failure.javaClass.simpleName}"
                    playPause.isEnabled = false
                }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun renderState() {
        val player = controller ?: return
        val title = player.mediaMetadata.title?.toString()
            ?: player.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "Aucun média"
        val groups = player.currentTracks.groups
        val hasAudio = groups.any { it.type == C.TRACK_TYPE_AUDIO }
        val hasVideo = groups.any { it.type == C.TRACK_TYPE_VIDEO }
        val state = when (player.playbackState) {
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_READY -> "Ready"
            Player.STATE_ENDED -> "Ended"
            else -> "Idle"
        }
        titleView.text = title
        stateView.text = "$state · Audio: ${if (hasAudio) "yes" else "no"} · Video: ${if (hasVideo) "yes" else "no"}"
        playPause.isEnabled = player.currentMediaItem != null
        playPause.text = if (player.isPlaying) "Pause" else "Lecture"
    }

    override fun onDestroy() {
        playerView.player = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onDestroy()
    }

    companion object {
        const val MAIN_APP_PACKAGE = "app.cleanroom.streamlab"
    }
}
