package app.cleanroom.streamlab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cleanroom.streamlab.data.CatalogChannelRow
import kotlinx.coroutines.delay

/**
 * Live TV controls drawn over the existing picture (the video surface is never rebuilt):
 * previous / next channel, pause, a channel list to zap without leaving full screen,
 * a short "number · name" banner on every change and a clear way out when a channel is down.
 */
@Composable
fun TvLiveOverlay(
    channels: List<CatalogChannelRow>,
    current: CatalogChannelRow,
    isPlaying: Boolean,
    failed: Boolean,
    loading: Boolean,
    onPlay: (CatalogChannelRow) -> Unit,
    onZap: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onExit: () -> Unit,
    onLock: () -> Unit,
    onPip: (() -> Unit)?,
    controlsTick: Int,
    modifier: Modifier = Modifier,
    nowNext: app.cleanroom.streamlab.core.NowNext.Result? = null,
    onExternal: (() -> Unit)? = null,
) {
    val number = channels.indexOfFirst { it.id == current.id } + 1
    var controlsVisible by remember { mutableStateOf(true) }
    var listOpen by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var banner by remember { mutableStateOf(true) }
    // Every tap on the picture (counted by the caller) shows the controls again.
    LaunchedEffect(controlsTick) { if (controlsTick > 0) { controlsVisible = !controlsVisible; interaction++ } }
    LaunchedEffect(interaction, controlsVisible, listOpen, failed) {
        if (controlsVisible && !listOpen && !failed) { delay(4_000); controlsVisible = false }
    }
    LaunchedEffect(current.id) { banner = true; delay(2_500); banner = false }
    fun touched() { interaction++ }

    Box(modifier.fillMaxSize().testTag("tv-live-overlay")) {
        // Channel banner: always shown briefly after zapping, even with controls hidden.
        AnimatedVisibility(banner && !controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            ChannelBadge(number, current.name)
        }
        AnimatedVisibility(controlsVisible || failed, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .70f), Color.Transparent, Color.Black.copy(alpha = .70f))))) {
                Row(Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onExit, modifier = Modifier.testTag("tv-back")) { SlIcon(R.drawable.ic_arrow_back, stringResource(R.string.tv_back), tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        ChannelBadge(number, current.name)
                        nowNext?.let { NowNextLine(it) }
                    }
                    if (onExternal != null) IconButton(onClick = onExternal, modifier = Modifier.testTag("tv-external")) {
                        SlIcon(R.drawable.ic_open_external, stringResource(R.string.external_player), tint = Color.White)
                    }
                    if (onPip != null) IconButton(onClick = onPip, modifier = Modifier.testTag("tv-pip")) { SlIcon(R.drawable.ic_pip, stringResource(R.string.tv_pip), tint = Color.White) }
                    IconButton(onClick = onLock, modifier = Modifier.testTag("player-lock")) { SlIcon(R.drawable.ic_lock, stringResource(R.string.player_lock), tint = Color.White) }
                }
                if (failed) {
                    Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(16.dp), color = Cinema.elevated.copy(alpha = .95f)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.tv_down_title), color = Cinema.white, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.player_failed_detail), color = Cinema.muted, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { onZap(1) }, modifier = Modifier.testTag("tv-next-after-error")) {
                                Text(stringResource(R.string.tv_next_channel))
                                Spacer(Modifier.width(8.dp))
                                SlIcon(R.drawable.ic_ctl_next, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                } else Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundControl(R.drawable.ic_ctl_previous, stringResource(R.string.tv_previous_channel), 56, "tv-prev") { touched(); onZap(-1) }
                    RoundControl(if (isPlaying || loading) R.drawable.ic_ctl_pause else R.drawable.ic_ctl_play,
                        stringResource(if (isPlaying) R.string.player_pause else R.string.player_play), 72, "tv-toggle") { touched(); onTogglePause() }
                    RoundControl(R.drawable.ic_ctl_next, stringResource(R.string.tv_next_channel), 56, "tv-next") { touched(); onZap(1) }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tv_swipe_hint), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Surface(onClick = { listOpen = true }, shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = .14f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .25f)), modifier = Modifier.testTag("tv-open-list")) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SlIcon(R.drawable.ic_list, null, Modifier.size(20.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tv_channels_button), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (listOpen) ChannelDrawer(channels, current, onPick = { onPlay(it); listOpen = false; controlsVisible = false }, onClose = { listOpen = false })
    }
}

@Composable
private fun ChannelBadge(number: Int, name: String, modifier: Modifier = Modifier) {
    Row(modifier.testTag("tv-channel-banner"), verticalAlignment = Alignment.CenterVertically) {
        if (number > 0) Surface(shape = RoundedCornerShape(8.dp), color = Cinema.mint) {
            Text("$number", color = Cinema.bg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoundControl(icon: Int, label: String, sizeDp: Int, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Black.copy(alpha = .45f), border = BorderStroke(1.dp, Color.White.copy(alpha = .3f)),
        modifier = Modifier.size(sizeDp.dp).testTag(tag).semantics { contentDescription = label }) {
        Box(contentAlignment = Alignment.Center) { SlIcon(icon, null, Modifier.size((sizeDp * .5f).dp), tint = Color.White) }
    }
}

@Composable
private fun ChannelDrawer(channels: List<CatalogChannelRow>, current: CatalogChannelRow, onPick: (CatalogChannelRow) -> Unit, onClose: () -> Unit) {
    val start = channels.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))) {
        val wide = maxWidth > maxHeight
        // Tap outside the list closes it.
        Surface(onClick = onClose, color = Color.Transparent, modifier = Modifier.fillMaxSize()) {}
        Surface(
            modifier = (if (wide) Modifier.fillMaxHeight().width(340.dp).align(Alignment.CenterEnd) else Modifier.fillMaxWidth().height(maxHeight * .6f).align(Alignment.BottomCenter))
                .testTag("tv-channel-drawer"),
            color = Cinema.elevated, shape = if (wide) RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tv_channels_button), color = Cinema.white, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { SlIcon(R.drawable.ic_expand_more, stringResource(R.string.field_cancel), tint = Cinema.muted) }
                }
                LazyColumn(state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(channels, key = { _, it -> it.id }) { index, channel ->
                        val selected = channel.id == current.id
                        Surface(onClick = { onPick(channel) }, color = if (selected) Cinema.raised else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().testTag("tv-drawer-${channel.id}")) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", color = if (selected) Cinema.mint else Cinema.muted, style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(40.dp))
                                ChannelLogo(channel.id, channel.name, channel.logo != null, 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(channel.name, color = Cinema.white, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (selected) SlIcon(R.drawable.ic_ctl_play, null, Modifier.size(18.dp), tint = Cinema.mint)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Now · 20:30–21:15" with a thin progress bar, then "Next 21:15 · title". Hidden without a guide. */
@Composable
fun NowNextLine(info: app.cleanroom.streamlab.core.NowNext.Result, light: Boolean = true) {
    val now = info.now
    val next = info.next
    if (now == null && next == null) return
    val main = if (light) Color.White else MaterialTheme.colorScheme.onSurface
    val soft = if (light) Color.White.copy(alpha = .75f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(top = 4.dp).testTag("tv-now-next"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (now != null) {
            Text(stringResource(R.string.epg_now, now.title, app.cleanroom.streamlab.core.NowNext.clock(now.startMs), app.cleanroom.streamlab.core.NowNext.clock(now.endMs)),
                color = main, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { app.cleanroom.streamlab.core.NowNext.progress(now, System.currentTimeMillis()) },
                modifier = Modifier.width(180.dp).height(3.dp), color = Cinema.mint, trackColor = soft.copy(alpha = .3f),
            )
        }
        if (next != null) Text(stringResource(R.string.epg_next, app.cleanroom.streamlab.core.NowNext.clock(next.startMs), next.title),
            color = soft, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
