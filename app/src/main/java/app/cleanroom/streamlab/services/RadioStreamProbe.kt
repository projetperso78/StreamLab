package app.cleanroom.streamlab.services

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.cleanroom.streamlab.StreamLabApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class RadioProbeResult(
    val works: Boolean,
    val audio: Boolean,
    val diagnostic: String,
)

@UnstableApi
class RadioStreamProbe(private val context: Context) {
    suspend fun probe(station: RadioStation, timeoutMs: Long = 10_000): RadioProbeResult =
        withContext(Dispatchers.Main.immediate) {
            val app = context.applicationContext as StreamLabApplication
            val deferred = CompletableDeferred<RadioProbeResult>()
            val factory = OkHttpDataSource.Factory(app.network.playerClient())
            val player = ExoPlayer.Builder(context).build()
            var audio = false

            fun updateTracks(tracks: Tracks) {
                audio = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }
            }
            fun completeReady() {
                updateTracks(player.currentTracks)
                deferred.complete(RadioProbeResult(audio, audio, if (audio) "OK" else "Aucune piste audio"))
            }

            player.volume = 0f
            player.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) = updateTracks(tracks)
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) completeReady()
                }
                override fun onPlayerError(error: PlaybackException) {
                    deferred.complete(RadioProbeResult(false, audio, error.errorCodeName))
                }
            })
            val item = MediaItem.fromUri(station.streamUrl)
            val source = if (station.streamUrl.substringBefore('?').endsWith(".m3u8", true))
                HlsMediaSource.Factory(factory).createMediaSource(item)
            else ProgressiveMediaSource.Factory(factory).createMediaSource(item)
            player.setMediaSource(source)
            player.prepare()

            try {
                withTimeoutOrNull(timeoutMs) { deferred.await() }
                    ?: RadioProbeResult(false, audio, "Délai dépassé")
            } finally {
                player.release()
            }
        }
}
