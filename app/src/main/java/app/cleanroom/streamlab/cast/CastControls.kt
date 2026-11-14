package app.cleanroom.streamlab.cast

import androidx.compose.material3.TextButton
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.session.MediaController
import androidx.mediarouter.app.MediaRouteButton
import app.cleanroom.streamlab.data.PlaybackSource
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

@Composable
fun CastControls(
    source: PlaybackSource?,
    category: String,
    controller: MediaController?,
    onStatus: (String) -> Unit,
) {
    val context = LocalContext.current
    val castContext = remember(context) { runCatching { CastContext.getSharedInstance(context) }.getOrNull() }
    val manager = castContext?.sessionManager
    val media = remember(source, category) {
        CastPolicy.mediaFor(source, category) ?: source?.url?.takeIf { it.startsWith("content://") }?.let { local ->
            // A video stored on the phone: served on the Wi-Fi only once the user casts it.
            val type = runCatching { context.contentResolver.getType(android.net.Uri.parse(local)) }.getOrNull() ?: "video/mp4"
            CastMedia(source.name, local, type, live = false)
        }
    }
    var connected by remember(manager) { mutableStateOf(manager?.currentCastSession?.isConnected == true) }

    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose { }
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) = Unit
            // Choosing a TV is enough: what is on screen is sent to it straight away.
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                connected = true
                if (media != null) sendToCast(context, castContext, media, controller, onStatus)
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) { connected = false }
            override fun onSessionSuspended(session: CastSession, reason: Int) { connected = false }
            override fun onSessionResuming(session: CastSession, sessionId: String) { connected = false }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) { connected = true }
            override fun onSessionResumeFailed(session: CastSession, error: Int) { connected = false }
            override fun onSessionEnding(session: CastSession) { connected = false }
            override fun onSessionEnded(session: CastSession, error: Int) { connected = false }
        }
        manager.addSessionManagerListener(listener, CastSession::class.java)
        onDispose { manager.removeSessionManagerListener(listener, CastSession::class.java) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (castContext != null) {
            AndroidView(
                factory = { viewContext ->
                    MediaRouteButton(ContextThemeWrapper(viewContext, app.cleanroom.streamlab.R.style.Theme_StreamLab_CastButton)).apply {
                        contentDescription = "Choisir un appareil Cast"
                        CastButtonFactory.setUpMediaRouteButton(viewContext.applicationContext, this)
                    }
                },
                modifier = Modifier.size(48.dp),
            )
        }
        // Already connected to a TV: one tap sends the current channel or station again.
        if (connected && media != null) TextButton(onClick = { sendToCast(context, castContext, media, controller, onStatus) }) {
            Text(androidx.compose.ui.res.stringResource(app.cleanroom.streamlab.R.string.cast_send_here))
        }
    }
}

private fun sendToCast(
    context: Context,
    castContext: CastContext?,
    media: CastMedia,
    controller: MediaController?,
    onStatus: (String) -> Unit,
) {
    val session = castContext?.sessionManager?.currentCastSession
    val remote = session?.remoteMediaClient
    val url = if (media.url.startsWith("content://")) {
        LocalCastServer.publish(context, android.net.Uri.parse(media.url), media.contentType) ?: run {
            onStatus(context.getString(app.cleanroom.streamlab.R.string.cast_needs_wifi))
            return
        }
    } else media.url
    if (session?.isConnected != true || remote == null) {
        onStatus("Cast : aucun appareil connecté")
        return
    }
    val metadataType = if (media.contentType.startsWith("audio/")) MediaMetadata.MEDIA_TYPE_MUSIC_TRACK else MediaMetadata.MEDIA_TYPE_MOVIE
    val metadata = MediaMetadata(metadataType).apply { putString(MediaMetadata.KEY_TITLE, media.title) }
    val info = MediaInfo.Builder(url)
        .setStreamType(if (media.live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
        .setContentType(media.contentType)
        .setMetadata(metadata)
        .build()
    val request = MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).apply {
        if (!media.live && (controller?.currentPosition ?: 0L) > 0L) setCurrentTime(controller?.currentPosition ?: 0L)
    }.build()
    remote.load(request).setResultCallback { result ->
        if (result.status.isSuccess) {
            controller?.pause()
            val name = session.castDevice?.friendlyName ?: "appareil Cast"
            onStatus("Cast : ${media.title} envoyé vers $name")
        } else {
            onStatus("Cast refusé par le récepteur")
        }
    }
}

