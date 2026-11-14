package app.cleanroom.streamlab

import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Search
import app.cleanroom.streamlab.core.TextMatch
import androidx.compose.runtime.Composable

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.Row

import androidx.compose.material3.Button

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.aspectRatio

import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.MaterialTheme

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import app.cleanroom.streamlab.core.ChannelOrigin
import app.cleanroom.streamlab.player.ZapList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.data.CatalogChannelRow

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TvPreviewPanel(allChannels: List<CatalogChannelRow>, controller: MediaController?, onSources: () -> Unit, onFullScreenChanged: (Boolean) -> Unit = {},
    onPip: (() -> Unit)? = null, onAddChannels: () -> Unit = onSources) {
    // One line per channel: copies of the same channel (HD, backup…) are its backup sources, not extra rows.
    val channels = remember(allChannels) {
        // Per playlist: backups are computed inside one playlist, so two playlists keep their own line.
        allChannels.distinctBy { it.profileId to app.cleanroom.streamlab.core.SourceFailover.sameChannelKey(it.name, it.tvgId) }
    }
    var selectedId by rememberSaveable { mutableStateOf(PipPolicy.lastTvChannelId) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf<String?>(null) }
    var touchLocked by rememberSaveable { mutableStateOf(false) }
    var tvPlaying by remember { mutableStateOf(false) }
    var tapTick by remember { mutableIntStateOf(0) }
    val uiLocale = LocalConfiguration.current.locales[0]
    val countries = remember(channels, uiLocale) {
        channels.mapNotNull { it.country }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { ChannelOrigin.countryName(it.key, uiLocale) })
            .map { it.key to it.value }
    }
    val healthContext = androidx.compose.ui.platform.LocalContext.current
    val healthVersion = ChannelHealth.version.intValue
    val deadIds = remember(healthVersion) { ChannelHealth.deadIds(healthContext) }
    // Out-of-service channels go last; they stay reachable, greyed out, for a retry.
    val filtered = channels.filter { TextMatch.matches(it.name, query) && (country == null || it.country == country) }
        .sortedBy { it.id in deadIds }
    val zapList = filtered.filterNot { it.id in deadIds }.ifEmpty { filtered }
    val healthApp = healthContext.applicationContext as StreamLabApplication
    val healthScope = rememberCoroutineScope()
    var checking by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var checkResult by remember { mutableStateOf<Int?>(null) }
    var statusId by remember { mutableIntStateOf(R.string.tv_connecting) }
    var playbackFailed by remember { mutableStateOf(false) }
    val visibilityPlayback = remember(controller) { PlaybackVisibilityPolicy() }
    val selected = channels.firstOrNull { it.id == selectedId } ?: channels.firstOrNull()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (controller?.playerError != null) {
                    statusId = R.string.tv_unavailable
                    playbackFailed = true
                    return
                }
                statusId = when (state) {
                    Player.STATE_BUFFERING -> R.string.tv_loading
                    Player.STATE_READY -> R.string.tv_ready
                    Player.STATE_ENDED -> R.string.tv_ended
                    else -> R.string.tv_waiting
                }
                playbackFailed = false
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                tvPlaying = playing
                if (playing) { statusId = R.string.tv_playing; playbackFailed = false; selectedId?.let { ChannelHealth.mark(healthContext, it, alive = true) } }
            }
            override fun onPlayerError(error: PlaybackException) {
                statusId = R.string.tv_unavailable; playbackFailed = true
                // The service tries the backups first: still failing a few seconds later means out of service.
                val failedId = selectedId ?: return
                healthScope.launch {
                    kotlinx.coroutines.delay(8_000)
                    if (playbackFailed && selectedId == failedId && controller?.isPlaying != true) ChannelHealth.mark(healthContext, failedId, alive = false)
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    visibilityPlayback.onPause(controller?.isPlaying == true)
                    controller?.pause()
                }
                Lifecycle.Event.ON_START -> if (visibilityPlayback.onResume()) controller?.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        controller?.addListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            controller?.removeListener(listener)
            visibilityPlayback.onPause(wasPlaying = false)
            // Going into the small window keeps the channel playing.
            if (!PipPolicy.inPip) controller?.pause()
        }
    }
    fun play(channel: CatalogChannelRow) {
        val player = controller ?: return
        statusId = R.string.tv_loading
        playbackFailed = false
        selectedId = channel.id
        player.setMediaItem(MediaItem.Builder().setMediaId(OpaqueMediaId.channel(channel.id)).build())
        player.prepare()
        player.play()
    }
    LaunchedEffect(selected?.id, controller) {
        val channel = selected ?: return@LaunchedEffect
        PipPolicy.lastTvChannelId = channel.id
        // Back from the small window: the channel is already on, do not restart it.
        val alreadyOn = controller?.currentMediaItem?.mediaId == app.cleanroom.streamlab.core.OpaqueMediaId.channel(channel.id) &&
            controller.playbackState != Player.STATE_IDLE
        if (!alreadyOn) play(channel)
    }
    LaunchedEffect(fullScreen) { onFullScreenChanged(fullScreen) }
    DisposableEffect(Unit) { onDispose { onFullScreenChanged(false) } }
    BackHandler(fullScreen && !touchLocked) { fullScreen = false }
    fun zap(delta: Int) {
        val list = zapList.ifEmpty { channels }
        val next = ZapList.step(list.map { it.id.toString() }, selected?.id?.toString(), delta)?.toLongOrNull() ?: return
        list.firstOrNull { it.id == next }?.let(::play)
    }
    if (selected == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SlIcon(R.drawable.ic_nav_tv, null, Modifier.size(56.dp), tint = Cinema.gold)
                Text(stringResource(R.string.tv_empty), style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Button(onClick = onAddChannels, modifier = Modifier.heightIn(min = 56.dp).testTag("tv-add-source")) {
                    Text(stringResource(R.string.tv_add_source), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        return
    }
    var multiview by rememberSaveable { mutableStateOf(false) }
    if (multiview) {
        // The main player stops while the tiles play: one sound at a time.
        // Whole screen for the tiles: the app's bars and title step aside, like full-screen TV.
        LaunchedEffect(Unit) { controller?.pause(); onFullScreenChanged(true) }
        val pool = zapList.ifEmpty { channels }
        val start = listOf(selected) + pool.filter { it.id != selected.id }.take(3)
        MultiviewPanel(channels = pool, start = start, onExit = { multiview = false; onFullScreenChanged(false); controller?.play() })
        return
    }
    ImmersiveFullScreen(fullScreen)
    val nowNext by produceState<app.cleanroom.streamlab.core.NowNext.Result?>(null, selected.id) {
        while (true) {
            value = runCatching { healthApp.repository.nowNext(selected.id) }.getOrNull()
            kotlinx.coroutines.delay(60_000)
        }
    }
    RegisterVideoForPip(tvPlaying)
    // Searching with the keyboard open: the picture shrinks (same surface, only resized) to leave room for results.
    val keyboardOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible

    @Composable
    fun PreviewPane(modifier: Modifier, portrait: Boolean) {
        Column(modifier) {
            Box((if (fullScreen) Modifier.fillMaxWidth().weight(1f)
                else if (portrait && keyboardOpen) Modifier.fillMaxWidth().height(96.dp)
                else if (portrait) Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                else Modifier.fillMaxWidth().weight(1f))) {
            AndroidView(
                // The app draws its own live-TV controls on top; Media3's VOD bar is not useful for live channels.
                factory = { context -> PlayerView(context).apply { player = controller; useController = false } },
                update = { view -> view.player = controller },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black).testTag("tv-preview").clickable { if (fullScreen) tapTick++ else fullScreen = true }
                    .pointerInput(fullScreen) {
                        if (!fullScreen) return@pointerInput
                        var travelled = 0f
                        detectVerticalDragGestures(
                            onDragStart = { travelled = 0f },
                            onDragEnd = { if (travelled < -120f) zap(1) else if (travelled > 120f) zap(-1) },
                        ) { _, dy -> travelled += dy }
                    },
            )
            if (fullScreen) TvLiveOverlay(
                nowNext = nowNext,
                onExternal = {
                    healthScope.launch {
                        runCatching { healthApp.repository.playbackSource(selected.id) }.getOrNull()?.let {
                            controller?.pause()
                            ExternalPlayer.open(healthContext, it.url, selected.name)
                        }
                    }
                },
                channels = zapList.ifEmpty { channels },
                current = selected,
                isPlaying = tvPlaying,
                failed = playbackFailed,
                loading = statusId == R.string.tv_loading,
                onPlay = ::play,
                onZap = ::zap,
                onTogglePause = { if (controller?.isPlaying == true) controller.pause() else controller?.play() },
                onExit = { fullScreen = false },
                onLock = { touchLocked = true },
                onPip = onPip,
                controlsTick = tapTick,
            ) else {
                FullScreenToggle(entering = true, onClick = { fullScreen = true }, modifier = Modifier.align(Alignment.TopEnd))
                // Send the channel to a TV (Chromecast / Google TV) on the same Wi-Fi.
                val castSource by produceState<app.cleanroom.streamlab.data.PlaybackSource?>(null, selected.id) {
                    value = runCatching { healthApp.repository.playbackSource(selected.id) }.getOrNull()
                }
                Box(Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(14.dp))) {
                app.cleanroom.streamlab.cast.CastControls(source = castSource, category = "live " + selected.category, controller = controller,
                    onStatus = { android.widget.Toast.makeText(healthContext, it, android.widget.Toast.LENGTH_SHORT).show() })
                }
            }
            }
            if (!fullScreen && !keyboardOpen) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selected.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    val guide = nowNext
                    if (guide?.now != null || guide?.next != null) NowNextLine(guide, light = false)
                    else Text(stringResource(statusId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (playbackFailed) TextButton(onClick = { play(selected) }) { Text(stringResource(R.string.tv_retry)) }
                Button(onClick = { fullScreen = true }, modifier = Modifier.testTag("tv-fullscreen")) {
                    SlIcon(R.drawable.ic_fullscreen, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tv_fullscreen))
                }
            }
        }
    }

    @Composable
    fun ListPane(modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FirstUseTip(FirstUseTips.TV, R.string.tip_tv_title,
                listOf(R.string.tip_tv_1, R.string.tip_tv_2, R.string.tip_tv_3, R.string.tip_tv_4))
            OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.tv_search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Search, null) }, shape = RoundedCornerShape(16.dp))
            if (countries.size > 1) {
                LazyRow(Modifier.fillMaxWidth().testTag("tv-country-filter"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = country == null, onClick = { country = null },
                            label = { Text(stringResource(R.string.channel_all_countries)) },
                            leadingIcon = { SlIcon(R.drawable.ic_globe, null, Modifier.size(18.dp), tint = Cinema.mint) })
                    }
                    items(countries, key = { it.first }) { (code, count) ->
                        FilterChip(
                            modifier = Modifier.testTag("tv-country-$code"),
                            selected = country == code,
                            onClick = { country = if (country == code) null else code },
                            label = { Text("${ChannelOrigin.flag(code)} ${ChannelOrigin.countryName(code, uiLocale)} ($count)") },
                        )
                    }
                }
            }
            // While typing a search, the action buttons step aside so the matching channels are visible.
            if (!keyboardOpen) androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.tv_channel_count, channels.size, channels.size), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp))
                TextButton(
                    onClick = {
                        if (checking == null) healthScope.launch {
                            checkResult = null
                            checking = 0 to filtered.size
                            checkResult = ChannelHealth.checkAll(healthApp, healthApp.repository, filtered.map { it.id }) { done, total -> checking = done to total }
                            checking = null
                        }
                    },
                    enabled = checking == null,
                    modifier = Modifier.testTag("tv-check-channels"),
                ) {
                    val progress = checking
                    Text(if (progress != null) stringResource(R.string.tv_checking, progress.first, progress.second) else stringResource(R.string.tv_check))
                }
                if (channels.size >= 2) TextButton(onClick = { multiview = true }, modifier = Modifier.testTag("tv-multiview")) {
                    Text(stringResource(R.string.multiview_title))
                }
                TextButton(onClick = onAddChannels, modifier = Modifier.testTag("tv-add-channels")) {
                    SlIcon(R.drawable.ic_add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tv_add_channels))
                }
            }
            checkResult?.let { dead -> Text(stringResource(R.string.tv_check_result, filtered.size - dead, filtered.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("tv-check-result")) }
            if (filtered.isEmpty()) Text(stringResource(R.string.tv_no_match))
            LazyColumn(Modifier.weight(1f).testTag("tv-channels")) {
                items(filtered, key = { it.id }) { channel ->
                    Surface(
                        onClick = { if (selectedId == channel.id) play(channel) else selectedId = channel.id },
                        color = if (selected.id == channel.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected.id == channel.id) Cinema.mint else MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).testTag("tv-channel-${channel.id}")
                            .alpha(if (channel.id in deadIds) .45f else 1f),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(channel.id, channel.name, channel.logo != null, 40.dp)
                            if (channel.id in deadIds) Text(stringResource(R.string.tv_out_of_service), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("tv-dead-${channel.id}"))
                            Text(channel.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            channel.country?.let { Text(ChannelOrigin.flag(it)) }
                            if (selected.id == channel.id) SlIcon(R.drawable.ic_ctl_play, null, Modifier.size(20.dp), tint = Cinema.mint)
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Portrait phones: picture on top, list below. Wide screens: side by side.
        val portraitNow = maxWidth < 600.dp && maxHeight > maxWidth
        // Rotating into full screen must not switch layouts, or the video surface is rebuilt.
        // Plain holder, not Compose state: this runs during measure, where state writes are forbidden.
        val lastPortrait = remember { booleanArrayOf(portraitNow) }
        // The keyboard shrinks the height: switching layouts then would rebuild the search field and close the keyboard.
        if (!fullScreen && !keyboardOpen) lastPortrait[0] = portraitNow
        val portrait = lastPortrait[0]
        // The preview keeps the same place in the tree when going full screen, so the video
        // surface is never recreated (a recreated surface stayed black on a real phone).
        if (portrait) Column(Modifier.fillMaxSize().testTag("tv-split")) {
            PreviewPane(if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(horizontal = 12.dp), portrait = !fullScreen)
            if (!fullScreen) ListPane(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp))
        } else Row(Modifier.fillMaxSize().testTag("tv-split")) {
            PreviewPane(if (fullScreen) Modifier.fillMaxSize() else Modifier.weight(0.46f).fillMaxHeight().padding(8.dp), portrait = false)
            if (!fullScreen) ListPane(Modifier.weight(0.54f).fillMaxHeight().padding(8.dp))
        }
    if (touchLocked) TouchLockOverlay(onUnlock = { touchLocked = false })
    }
}
