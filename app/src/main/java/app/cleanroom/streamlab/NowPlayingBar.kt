package app.cleanroom.streamlab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController

/** What the player is doing, read from the shared controller (song from the radio when it sends one). */
internal data class NowPlayingState(val mediaId: String?, val title: String, val subtitle: String?, val playing: Boolean)

internal fun Player.nowPlayingState(): NowPlayingState? {
    val item = currentMediaItem ?: return null
    if (playbackState == Player.STATE_IDLE && !playWhenReady) return null
    val meta = mediaMetadata
    val title = (meta.title ?: item.mediaMetadata.title)?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val station = (meta.station ?: meta.subtitle)?.toString()
    val subtitle = listOfNotNull(meta.artist?.toString(), station?.takeIf { it != meta.artist?.toString() })
        .filter { it.isNotBlank() && it != title }.joinToString(" · ").takeIf { it.isNotBlank() }
    return NowPlayingState(item.mediaId, title, subtitle, isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING))
}

/** Mini player shown above the tabs, like music apps: what is on, play/pause, tap to open. */
@Composable
fun NowPlayingBar(controller: MediaController?, onOpen: (String?) -> Unit, modifier: Modifier = Modifier) {
    if (controller == null) return
    var state by remember(controller) { mutableStateOf(controller.nowPlayingState()) }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { state = controller.nowPlayingState() }
        }
        controller.addListener(listener)
        state = controller.nowPlayingState()
        onDispose { controller.removeListener(listener) }
    }
    val current = state ?: return
    val openLabel = stringResource(R.string.now_playing_open, current.title)
    Surface(
        onClick = { onOpen(current.mediaId) },
        modifier = modifier.fillMaxWidth().semantics { contentDescription = openLabel }.testTag("now-playing-bar"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_nav_radio), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(current.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("now-playing-title"))
                Text(current.subtitle ?: stringResource(if (current.playing) R.string.now_playing_live else R.string.now_playing_paused),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val toggleLabel = stringResource(if (current.playing) R.string.widget_pause else R.string.widget_play)
            IconButton(
                onClick = { if (current.playing) controller.pause() else controller.play() },
                modifier = Modifier.semantics { contentDescription = toggleLabel }.testTag("now-playing-toggle"),
            ) {
                Icon(painterResource(if (current.playing) R.drawable.ic_ctl_pause else R.drawable.ic_ctl_play), contentDescription = null,
                    modifier = Modifier.size(30.dp))
            }
        }
    }
}
