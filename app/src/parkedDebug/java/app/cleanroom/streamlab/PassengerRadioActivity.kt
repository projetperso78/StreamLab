package app.cleanroom.streamlab

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.launch

/** Keeps the country picker and playback on the projected passenger surface. */
@UnstableApi
class PassengerRadioActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var status by mutableStateOf("")
    private var playing by mutableStateOf(false)
    private var selected by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(StreamLabLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = getString(R.string.pass_radio_select)
        val app = application as StreamLabApplication
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(app.network.playerClient())))
            .build().apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(value: Boolean) { playing = value }
                    override fun onPlayerError(error: PlaybackException) { status = getString(R.string.pass_radio_station_unavailable) }
                })
            }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().systemBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(8.dp)) {
                            TextButton(onClick = { finish() }) { Text(getString(R.string.pass_services_back)) }
                            Text(status, Modifier.weight(1f).padding(8.dp), maxLines = 2)
                            if (selected) Button(onClick = {
                                if (player.isPlaying) player.pause() else { if (player.playerError != null) player.prepare(); player.play() }
                            }) { Text(getString(if (playing) R.string.pass_radio_pause else R.string.pass_radio_play)) }
                        }
                        Box(Modifier.weight(1f)) {
                            RadioPanel(app, onStatus = { status = it }, onAdded = { id, _ ->
                                lifecycleScope.launch {
                                    runCatching {
                                        val source = app.repository.playbackSource(id)
                                        status = source.name
                                        player.setMediaItem(MediaItem.fromUri(source.url))
                                        selected = true
                                        player.prepare(); player.play()
                                    }.onFailure { status = getString(R.string.pass_radio_start_error) }
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        // Radio is sound only: it keeps playing while driving; leaving with Back stops it.
        if (isFinishing && ::player.isInitialized) player.pause()
        super.onPause()
    }
    override fun onDestroy() { player.release(); super.onDestroy() }
}
