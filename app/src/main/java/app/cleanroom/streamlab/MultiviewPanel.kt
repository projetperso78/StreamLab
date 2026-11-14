package app.cleanroom.streamlab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.cleanroom.streamlab.core.HeaderPolicy
import app.cleanroom.streamlab.data.CatalogChannelRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multiview (2 to 4 live channels at once). Each tile has its own small player in reduced
 * quality; only the selected tile has sound. Tap a tile for its sound, "Changer" to swap it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MultiviewPanel(channels: List<CatalogChannelRow>, start: List<CatalogChannelRow>, onExit: () -> Unit) {
    val tiles = remember { mutableStateListOf<CatalogChannelRow>().apply { addAll(start.take(4)) } }
    var count by remember { mutableIntStateOf(tiles.size.coerceIn(2, 4)) }
    var audible by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<Int?>(null) }
    BackHandler(onBack = onExit)
    ImmersiveFullScreen(true)
    RegisterVideoForPip(false)

    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().testTag("multiview")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit, modifier = Modifier.testTag("multiview-exit")) { SlIcon(R.drawable.ic_arrow_back, stringResource(R.string.tv_back), tint = Color.White) }
            Text(stringResource(R.string.multiview_title), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            listOf(2, 3, 4).forEach { n ->
                FilterChip(selected = count == n, onClick = {
                    count = n
                    while (tiles.size < n) channels.firstOrNull { c -> tiles.none { it.id == c.id } }?.let { tiles.add(it) } ?: break
                    if (audible >= n) audible = 0
                }, label = { Text("$n") }, modifier = Modifier.padding(start = 6.dp).testTag("multiview-count-$n"))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > maxHeight
            val shown = tiles.take(count)
            // 2: side by side (wide) or stacked; 3-4: a 2x2 grid.
            val rows = if (shown.size <= 2) (if (wide) listOf(shown) else shown.map { listOf(it) }) else shown.chunked(2)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { channel ->
                            val index = shown.indexOf(channel)
                            MultiviewTile(
                                channel = channel,
                                audible = index == audible,
                                onSelect = { audible = index },
                                onChange = { picking = index },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
    picking?.let { index ->
        AlertDialog(onDismissRequest = { picking = null }, confirmButton = {}, dismissButton = {
            TextButton(onClick = { picking = null }) { Text(stringResource(R.string.field_cancel)) }
        }, title = { Text(stringResource(R.string.multiview_pick)) }, text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(channels, key = { it.id }) { channel ->
                    Text(channel.name, modifier = Modifier.fillMaxWidth().clickable {
                        if (index < tiles.size) tiles[index] = channel else tiles.add(channel)
                        picking = null
                    }.padding(vertical = 12.dp).testTag("multiview-pick-${channel.id}"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        })
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MultiviewTile(channel: CatalogChannelRow, audible: Boolean, onSelect: () -> Unit, onChange: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as StreamLabApplication
    val player = remember(channel.id) {
        ExoPlayer.Builder(context).build().apply {
            // Small tiles: standard quality is plenty and keeps four streams light.
            trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSizeSd().build()
            volume = 0f
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(audible, player) { withContext(Dispatchers.Main) { player.volume = if (audible) 1f else 0f } }
    LaunchedEffect(channel.id) {
        val source = withContext(Dispatchers.IO) { runCatching { app.repository.playbackSource(channel.id) }.getOrNull() } ?: return@LaunchedEffect
        val factory = OkHttpDataSource.Factory(app.network.playerClient()).setDefaultRequestProperties(HeaderPolicy.sanitize(source.headers))
        val item = MediaItem.fromUri(source.url)
        val media = if (source.url.substringBefore('?').endsWith(".m3u8", true)) HlsMediaSource.Factory(factory).createMediaSource(item)
        else ProgressiveMediaSource.Factory(factory).createMediaSource(item)
        // A player only accepts calls from the main thread.
        withContext(Dispatchers.Main) {
            player.setMediaSource(media)
            player.prepare()
            player.play()
        }
    }
    Box(modifier.background(Color.Black)
        .then(if (audible) Modifier.border(2.dp, Cinema.mint) else Modifier)
        .clickable(onClick = onSelect).testTag("multiview-tile-${channel.id}")) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = .6f),
            border = if (audible) BorderStroke(1.5.dp, Cinema.mint) else null,
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (audible) {
                    SlIcon(R.drawable.ic_music_note, null, Modifier.size(14.dp), tint = Cinema.mint)
                    Spacer(Modifier.width(4.dp))
                }
                Text(channel.name, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = onChange, modifier = Modifier.align(Alignment.BottomEnd).testTag("multiview-change-${channel.id}")) {
            Text(stringResource(R.string.multiview_change), color = Color.White)
        }
    }
}
