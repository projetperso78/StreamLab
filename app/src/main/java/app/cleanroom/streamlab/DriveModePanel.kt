package app.cleanroom.streamlab

import androidx.compose.ui.res.painterResource

import androidx.compose.material3.Icon

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import app.cleanroom.streamlab.data.CatalogRepository
import app.cleanroom.streamlab.player.SleepTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Mode Trajet": one tap resumes the last radio/channel with very large controls.
 * Audio-first; meant to be set up before driving (Android Auto takes over on the car screen).
 */
@Composable
fun DriveModePanel(controller: MediaController?, repo: CatalogRepository, onChooseRadio: () -> Unit, onExit: () -> Unit) {
    var title by rememberSaveable { mutableStateOf<String?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var nothingToResume by rememberSaveable { mutableStateOf(false) }
    var sleepMinutes by rememberSaveable { mutableIntStateOf(0) }
    var sleepEndAt by rememberSaveable { mutableLongStateOf(0L) }
    var now by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                title = (metadata.station ?: metadata.title)?.toString() ?: title
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        controller?.addListener(listener)
        controller?.let {
            playing = it.isPlaying
            title = (it.mediaMetadata.station ?: it.mediaMetadata.title)?.toString() ?: title
        }
        onDispose { controller?.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        val player = controller ?: return@LaunchedEffect
        if (player.currentMediaItem != null) {
            if (!player.isPlaying) player.play()
            return@LaunchedEffect
        }
        val mediaId = runCatching { repo.resumeMediaId() }.getOrNull()
        if (mediaId == null) { nothingToResume = true; return@LaunchedEffect }
        // MediaController must only be touched on its application (main) thread, whatever thread the query resumed on.
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
            player.prepare()
            player.play()
        }
    }

    LaunchedEffect(sleepEndAt) {
        while (sleepEndAt > 0L) {
            now = System.currentTimeMillis()
            if (now >= sleepEndAt) { sleepEndAt = 0L; sleepMinutes = 0; break }
            delay(15_000)
        }
    }

    fun setSleep(minutes: Int) {
        val player = controller ?: return
        sleepMinutes = minutes
        sleepEndAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        now = System.currentTimeMillis()
        player.sendCustomCommand(SessionCommand(SleepTimer.COMMAND, Bundle.EMPTY), Bundle().apply { putInt(SleepTimer.ARG_MINUTES, minutes) })
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("drive-mode"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.drive_now_playing), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            when {
                title != null -> title!!
                nothingToResume -> stringResource(R.string.drive_nothing)
                else -> stringResource(R.string.player_connecting)
            },
            modifier = Modifier.fillMaxWidth().testTag("drive-title"),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        val previousLabel = stringResource(R.string.drive_previous)
        val nextLabel = stringResource(R.string.drive_next)
        val playLabel = stringResource(if (playing) R.string.player_pause else R.string.player_play)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledTonalButton(
                onClick = { controller?.seekToPrevious() },
                modifier = Modifier.size(96.dp).semantics { contentDescription = previousLabel }.testTag("drive-previous"),
                shape = CircleShape,
            ) { Icon(painterResource(R.drawable.ic_ctl_previous), contentDescription = null, modifier = Modifier.size(40.dp)) }
            Button(
                onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                modifier = Modifier.size(132.dp).semantics { contentDescription = playLabel }.testTag("drive-play"),
                shape = CircleShape,
            ) { Icon(painterResource(if (playing) R.drawable.ic_ctl_pause else R.drawable.ic_ctl_play), contentDescription = null, modifier = Modifier.size(64.dp)) }
            FilledTonalButton(
                onClick = { controller?.seekToNext() },
                modifier = Modifier.size(96.dp).semantics { contentDescription = nextLabel }.testTag("drive-next"),
                shape = CircleShape,
            ) { Icon(painterResource(R.drawable.ic_ctl_next), contentDescription = null, modifier = Modifier.size(40.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.drive_sleep_title), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("drive-sleep")) {
            items(SleepTimer.choices) { minutes ->
                FilterChip(
                    selected = sleepMinutes == minutes,
                    onClick = { setSleep(minutes) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("drive-sleep-$minutes"),
                    label = { Text(if (minutes == 0) stringResource(R.string.drive_sleep_off) else stringResource(R.string.drive_sleep_minutes, minutes)) },
                )
            }
        }
        if (sleepEndAt > 0L) {
            Text(stringResource(R.string.drive_sleep_left, SleepTimer.minutesLeft(sleepEndAt, now)),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("drive-sleep-left"))
        }
        OutlinedButton(onClick = onChooseRadio, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.drive_choose_radio))
        }
        Text(stringResource(R.string.drive_safety), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onExit, modifier = Modifier.testTag("drive-exit")) { Text(stringResource(R.string.drive_exit)) }
    }
}
