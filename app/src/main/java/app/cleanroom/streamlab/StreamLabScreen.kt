package app.cleanroom.streamlab

import androidx.compose.runtime.mutableFloatStateOf
import androidx.media3.common.MediaItem
import androidx.compose.ui.graphics.asImageBitmap
import app.cleanroom.streamlab.core.TextMatch
import androidx.compose.ui.res.painterResource

import androidx.compose.material3.Icon

import androidx.activity.compose.BackHandler

import app.cleanroom.streamlab.core.ChannelOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyRow
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.cleanroom.streamlab.cast.CastControls
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.jobs.ProgrammeReminder
import app.cleanroom.streamlab.core.ContinueWatching
import app.cleanroom.streamlab.core.Redaction
import app.cleanroom.streamlab.data.*
import app.cleanroom.streamlab.data.ProfileEntity as CatalogProfile
import app.cleanroom.streamlab.jobs.RefreshScheduler
import app.cleanroom.streamlab.player.AutoLibrary
import app.cleanroom.streamlab.services.LaunchResult
import app.cleanroom.streamlab.services.ServiceLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val ink = Color(0xFF10181B)
private val accent = Color(0xFF8EE2C1)
private val panels = Color(0xFF1A282C)
private data class NavItem(val id: String, val icon: Int)
private val sections = listOf(
    NavItem("Apps", R.drawable.ic_nav_home),
    NavItem("Radio", R.drawable.ic_nav_radio),
    NavItem("Live", R.drawable.ic_nav_tv),
    NavItem("Videos", R.drawable.ic_nav_video),
    NavItem("Favorites", R.drawable.ic_nav_favorite),
    NavItem("More", R.drawable.ic_nav_more),
)
private fun sectionTitle(section: String): String = when (section) {
    "Library" -> "Sources"
    "Live" -> "TV"
    "Radio" -> "Radio"
    "Videos" -> "Vidéos"
    "Favorites" -> "Favoris"
    "More" -> "Plus"
    "Guide" -> "Guide TV"
    "Apps" -> "Services"
    "Settings" -> "Réglages"
    else -> section
}
private fun sectionSubtitle(section: String): String = when (section) {
    "Library" -> "TV, radios, vidéos et sources au même endroit."
    "Live" -> "Tes chaînes prêtes à regarder."
    "Radio" -> "Choisis un pays puis une station."
    "Videos" -> "Tes films et vidéos ajoutés depuis le téléphone."
    "Favorites" -> "Tes contenus enregistrés pour les retrouver vite."
    "More" -> "Guide TV, services, Android Auto et réglages."
    "Guide" -> "Les programmes associés à tes sources TV."
    "Apps" -> "Raccourcis vers les applis officielles de tes services."
    "Settings" -> "Personnalise l’affichage et vérifie les capacités."
    else -> ""
}

@Composable private fun localizedNavLabel(id: String): String = stringResource(when (id) {
    "Apps" -> R.string.nav_home
    "Live" -> R.string.nav_tv
    "Radio" -> R.string.nav_radio
    "Videos" -> R.string.nav_videos
    "Favorites" -> R.string.nav_favorites
    else -> R.string.nav_more
})

@Composable private fun localizedSectionTitle(id: String): String = stringResource(when (id) {
    "Library" -> R.string.title_sources
    "Live" -> R.string.title_live
    "Radio" -> R.string.title_radio
    "Videos" -> R.string.title_videos
    "Favorites" -> R.string.title_favorites
    "More" -> R.string.title_more
    "Guide" -> R.string.title_guide
    "Apps" -> R.string.title_apps
    "Drive" -> R.string.drive_title
    "Share" -> R.string.share_title
    "Alarm" -> R.string.alarm_title
    else -> R.string.title_settings
})

@Composable private fun localizedSectionSubtitle(id: String): String = stringResource(when (id) {
    "Library" -> R.string.subtitle_sources
    "Live" -> R.string.subtitle_live
    "Radio" -> R.string.subtitle_radio
    "Videos" -> R.string.subtitle_videos
    "Favorites" -> R.string.subtitle_favorites
    "More" -> R.string.subtitle_more
    "Guide" -> R.string.subtitle_guide
    "Apps" -> R.string.subtitle_apps
    "Drive" -> R.string.drive_subtitle
    "Share" -> R.string.share_subtitle
    "Alarm" -> R.string.alarm_subtitle
    else -> R.string.subtitle_settings
})

@Composable
fun StreamLabScreen(app: StreamLabApplication, controller: MediaController?, isPip: Boolean, initialSection: String = "Apps", sectionRequestId: Int = 0, onPip: () -> Unit) {
    val repo = app.repository
    val prefs by app.preferences.values.collectAsState(initial = AppPreferences())
    val profiles by repo.profiles.collectAsState(initial = emptyList())
    val channels by repo.channels.collectAsState(initial = emptyList())
    val inProgress by repo.inProgress.collectAsState(initial = emptyList())
    val autoLibrary = remember(profiles, channels) { AutoLibrary(profiles, channels) }
    val favoriteRadios = remember(channels, autoLibrary) { channels.filter { it.favorite && autoLibrary.isRadioChannel(it) } }
    val continueItems = remember(inProgress, channels, autoLibrary) {
        val byId = channels.associateBy { it.id }
        inProgress.filter { ContinueWatching.isResumable(it.positionMs, it.durationMs) }
            .mapNotNull { recent -> byId[recent.channelId]?.let { ContinueItem(it, recent.positionMs, recent.durationMs) } }
            // Radios are live: never "continue watching", even if an old version saved a position.
            .filterNot { autoLibrary.isRadioChannel(it.channel) }
            .take(10)
    }
    var section by remember(initialSection, sectionRequestId) { mutableStateOf(if (initialSection == "RadioNear") "Radio" else initialSection) }
    var openNearRadio by remember(initialSection, sectionRequestId) { mutableStateOf(initialSection == "RadioNear") }
    var selectedProfile by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf<CatalogChannelRow?>(null) }
    var pendingRadioPlayId by remember { mutableStateOf<Long?>(null) }
    var pendingVideoPlayId by remember { mutableStateOf<Long?>(null) }
    var browsingShare by remember { mutableStateOf<Long?>(null) }
    var chooseVideoSource by remember { mutableStateOf(false) }
    var addPresetType by remember { mutableStateOf<ProfileType?>(null) }
    var status by remember { mutableStateOf<UiText>(UiText.Empty) }
    // Messages are short-lived: they fade after a few seconds instead of staying on every screen.
    LaunchedEffect(status) {
        if (status != UiText.Empty) { kotlinx.coroutines.delay(5_000); status = UiText.Empty }
    }
    var tvFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(channels, pendingVideoPlayId) {
        val pending = pendingVideoPlayId ?: return@LaunchedEffect
        val row = channels.firstOrNull { it.id == pending } ?: return@LaunchedEffect
        pendingVideoPlayId = null
        playing = row
        section = "Videos"
        runCatching { repo.markPlayed(row.id) }
    }
    LaunchedEffect(channels, pendingRadioPlayId) {
        val pending = pendingRadioPlayId ?: return@LaunchedEffect
        val row = channels.firstOrNull { it.id == pending } ?: return@LaunchedEffect
        pendingRadioPlayId = null
        playing = row
        section = "Radio"
        runCatching { repo.markPlayed(row.id) }
    }
    var showAdd by remember { mutableStateOf(false) }
    var addPresetName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CatalogProfile?>(null) }
    var localImportName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val serviceLauncher = remember(context) { ServiceLauncher(context) }
    val serviceUnavailableMessage = stringResource(R.string.apps_unavailable)
    val localListName = stringResource(R.string.local_list_name)
    val localVideoName = stringResource(R.string.profile_type_local_media)
    val localPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scope.launch {
                runCatching {
                    val id = repo.createProfile(localImportName.ifBlank { "Liste locale" }, ProfileType.LOCAL_M3U, uri.toString())
                    repo.refreshProfile(id)
                }.onSuccess { count -> status = localizedUiText(R.string.result_local_import, count) }
                    .onFailure { status = UiText.Literal(Redaction.diagnostic(it.message)) }
            }
        }
    }
    val localMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull().orEmpty().ifBlank { localVideoName }.take(80)
            scope.launch {
                runCatching { repo.createProfile(displayName, ProfileType.LOCAL_MEDIA, uri.toString()) }
                    .onSuccess { section = "Videos"; status = localizedUiText(R.string.result_video_added, displayName) }
                    .onFailure { status = UiText.Literal(Redaction.diagnostic(it.message)) }
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(repo.exportPortable(profiles).toByteArray()) }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(1_000_001) } ?: error("Cannot read document") }
                repo.importPortable(json)
            }.onSuccess { status = localizedUiText(R.string.result_portable_import, it) }
                .onFailure { status = UiText.Literal(Redaction.diagnostic(it.message)) }
        }
    }

    val backTarget = MainNavigationLayout.backTarget(section)
    BackHandler(enabled = playing != null || backTarget != null) {
        if (playing != null) playing = null else backTarget?.let { section = it }
    }

    val dark = prefs.theme == "dark" || (prefs.theme == "system" && androidx.compose.foundation.isSystemInDarkTheme())
    // Small window outside the app: only the picture of what is playing.
    if (isPip && playing == null) { PipVideo(controller); return }
    // The cinema palette is StreamLab's look; "light" in Settings keeps a light variant for those who need it.
    MaterialTheme(colorScheme = if (dark || prefs.theme == "system") Cinema.colors else lightColorScheme(primary = Color(0xFF006B50))) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            val screenModifier = if (isPip && playing != null) Modifier.fillMaxSize() else Modifier.fillMaxSize().safeDrawingPadding()
            BoxWithConstraints(screenModifier) {
            val wide = maxWidth >= 700.dp
            val compactNavigation = MainNavigationLayout.iconOnly(
                maxWidth.value.toInt(), LocalDensity.current.fontScale,
            )
            Row(Modifier.fillMaxSize()) {
                if (wide && !tvFullScreen) NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(20.dp))
                    sections.forEach { item ->
                        NavigationRailItem(
                            modifier = Modifier.testTag("nav-${item.id.lowercase()}"),
                            selected = section == item.id,
                            onClick = { section = item.id; playing = null },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(localizedNavLabel(item.id)) },
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    if (playing != null) {
                        PlayerPanel(playing!!, controller, repo, isPip = isPip, onBack = { playing = null }, onPip = onPip, onStatus = { status = UiText.Literal(it) },
                            onFullScreenChanged = { tvFullScreen = it })
                    } else {
                        if (!tvFullScreen && section != "Apps") Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(localizedSectionTitle(section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            val subtitle = localizedSectionSubtitle(section)
                            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.weight(1f)) { when (section) {
                            "Library" -> LibraryPanel(
                                profiles = profiles,
                                channels = channels,
                                onResume = { channel -> playing = channel; scope.launch { repo.markPlayed(channel.id) } },
                                onFavorites = { section = "Favorites" },
                                onTv = { section = "Live" },
                                onRadio = { section = "Radio" },
                                onVideos = { section = "Videos" },
                                onAdd = { addPresetName = ""; showAdd = true },
                                onLocal = {
                                    localImportName = localListName
                                    localPicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*"))
                                },
                                onVideo = { localMediaPicker.launch(arrayOf("video/*")) },
                                onDemo = { scope.launch {
                                    runCatching { repo.importDemoFixture() }
                                        .onSuccess { status = UiText.Resource(R.string.result_demo_loaded) }
                                        .onFailure { status = UiText.Literal(Redaction.diagnostic(it.message)) }
                                } },
                                onHls = { scope.launch {
                                    runCatching { repo.createProfile(PublicTestStream.TITLE, ProfileType.MANUAL, PublicTestStream.URL) }
                                        .onSuccess { status = UiText.Resource(R.string.result_hls_loaded) }
                                        .onFailure { status = UiText.Literal(Redaction.diagnostic(it.message)) }
                                } },
                                onExport = { exportPicker.launch("streamlab-profiles.json") },
                                onImport = { importPicker.launch(arrayOf("application/json")) },
                                onRefresh = { id -> scope.launch {
                                    runCatching { repo.refreshProfile(id) }
                                        .onSuccess { status = localizedUiText(R.string.result_channels_loaded, it) }
                                        .onFailure { status = UiText.Literal(Redaction.diagnostic(it.message)) }
                                } },
                                onEpg = { id -> scope.launch {
                                    runCatching { repo.refreshGuide(id) }
                                        .onSuccess {
                                            selectedProfile = id
                                            section = "Guide"
                                            status = localizedUiText(R.string.result_programs_loaded, it)
                                        }
                                        .onFailure { status = UiText.Literal(Redaction.diagnostic(it.message)) }
                                } },
                                onEdit = { editing = it },
                                onSchedule = { id, epg, hours -> scope.launch {
                                    repo.setRefreshHours(id, epg, hours)
                                    RefreshScheduler.periodic(context, id, epg, hours)
                                    status = if (hours > 0) localizedUiText(R.string.result_refresh_every, hours)
                                    else UiText.Resource(R.string.result_refresh_stopped)
                                } },
                                onDelete = { id -> scope.launch { repo.deleteProfile(id) } },
                            )
                            "Live" -> TvPreviewPanel(
                                onPip = onPip,
                                onAddChannels = { addPresetName = ""; showAdd = true },
                                allChannels = channels.filterNot { autoLibrary.isRadioChannel(it) || it.category == "Local video" },
                                controller = controller,
                                onSources = { section = "Library" },
                                onFullScreenChanged = { tvFullScreen = it },
                            )
                            "Videos" -> ChannelPanel(
                                channels = channels.filter { it.category == "Local video" },
                                dense = prefs.dense,
                                searchLabel = stringResource(R.string.channel_search_videos),
                                emptyMessage = stringResource(R.string.channel_empty_videos),
                                showFilters = false,
                                addLabel = stringResource(R.string.videos_add),
                                onAdd = { chooseVideoSource = true },
                                onFavorite = { id -> scope.launch { repo.toggleFavorite(id) } },
                                onPlay = { channel -> playing = channel; scope.launch { repo.markPlayed(channel.id) } },
                            )
                            "Favorites" -> ChannelPanel(
                                channels = channels.filter { it.favorite },
                                dense = prefs.dense,
                                searchLabel = stringResource(R.string.channel_search_favorites),
                                emptyMessage = stringResource(R.string.channel_empty_favorites),
                                emptyActionLabel = stringResource(R.string.favorites_explore_radio),
                                onEmptyAction = { section = "Radio" },
                                // Favourite radios in rotation: next/previous go through them, and a dead
                                // station hands over to the next one by itself.
                                addLabel = stringResource(R.string.favorites_radio_playlist).takeIf { favoriteRadios.size >= 2 },
                                onAdd = {
                                    controller?.run {
                                        setMediaItems(favoriteRadios.map { MediaItem.Builder().setMediaId(OpaqueMediaId.channel(it.id)).build() })
                                        repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                        prepare(); play()
                                    }
                                    status = UiText.Resource(R.string.favorites_radio_playlist_started)
                                },
                                onFavorite = { id -> scope.launch { repo.toggleFavorite(id) } },
                                onPlay = { channel -> playing = channel; scope.launch { repo.markPlayed(channel.id) } },
                            )
                            "Radio" -> RadioPanel(
                                app = app,
                                openNearOnStart = openNearRadio,
                                onNearOpened = { openNearRadio = false },
                                onAdded = { id, message -> pendingRadioPlayId = id; status = UiText.Literal(message) },
                                onStatus = { status = UiText.Literal(it) },
                            )
                            "More" -> MorePanel(
                                onSources = { section = "Library" },
                                onGuide = { section = "Guide" },
                                onApps = { section = "Apps" },
                                onSettings = { section = "Settings" },
                                onShare = { section = "Share" },
                                onAlarm = { section = "Alarm" },
                            )
                            "Guide" -> GuidePanel(profiles, selectedProfile, onSelect = { selectedProfile = it }, repo = repo)
                            "Alarm" -> AlarmPanel(repo = repo)
                            "Share" -> SharePanel(repo = repo, onStatus = { status = UiText.Literal(it) })
                            "Drive" -> DriveModePanel(
                                controller = controller,
                                repo = repo,
                                onChooseRadio = { section = "Radio" },
                                onExit = { section = "Apps" },
                            )
                            "Apps" -> CinemaPassengerHome(
                                embedded = true,
                                onOpenService = { service ->
                                    controller?.pause()
                                    if (serviceLauncher.launch(service) == LaunchResult.UNAVAILABLE) status = UiText.Literal(serviceUnavailableMessage)
                                },
                                onOpenSection = { target ->
                                    section = target
                                    playing = null
                                },
                                continueItems = continueItems,
                                onResume = { row -> playing = row; scope.launch { repo.markPlayed(row.id) } },
                            )
                            "Settings" -> SettingsPanel(prefs, app.preferences, onLanguageChange = { lang -> (context as? MainActivity)?.updateAppLanguage(lang) }, onUpdate = { block -> scope.launch { block() } })
                        }
                        // Floating message: it never pushes the screen down under the user's finger.
                        if (status != UiText.Empty && !tvFullScreen) Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().testTag("status-message"),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 6.dp,
                        ) {
                            Text(resolveUiText(status), Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                        }
                        }
                    }
                    if (playing == null && !tvFullScreen && section != "Live" && section != "Drive") {
                        NowPlayingBar(
                            controller = controller,
                            onOpen = { mediaId ->
                                val row = OpaqueMediaId.channelId(mediaId)?.let { id -> channels.firstOrNull { it.id == id } }
                                if (row != null) playing = row else section = "Drive"
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    if (!wide && playing == null && !tvFullScreen) NavigationBar {
                        sections.forEach { item ->
                            val label = localizedNavLabel(item.id)
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav-${item.id.lowercase()}")
                                    .semantics { contentDescription = label },
                                selected = section == item.id,
                                onClick = { section = item.id },
                                icon = { Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                                label = if (compactNavigation) null else {{ Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }},
                                alwaysShowLabel = !compactNavigation,
                            )
                        }
                    }
                }
            }
        }
        }
        editing?.let { current -> EditProfileDialog(current, onDismiss = { editing = null }, onSave = { name, source, username, password, epg ->
            editing = null
            scope.launch { runCatching { repo.updateProfile(current.id, name, source, username, password, epg) }
                .onSuccess { status = UiText.Resource(R.string.result_source_updated) }
                .onFailure { status = UiText.Literal(Redaction.diagnostic(it.message)) } }
        }) }
        if (chooseVideoSource) AlertDialog(onDismissRequest = { chooseVideoSource = false }, confirmButton = {},
            dismissButton = { TextButton(onClick = { chooseVideoSource = false }) { Text(stringResource(R.string.field_cancel)) } },
            title = { Text(stringResource(R.string.videos_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AddChoice(R.drawable.ic_folder, R.string.videos_from_phone, R.string.videos_from_phone_desc, "videos-from-phone") {
                        chooseVideoSource = false; localMediaPicker.launch(arrayOf("video/*"))
                    }
                    AddChoice(R.drawable.ic_link, R.string.add_choice_share, R.string.add_choice_share_desc, "videos-from-share") {
                        chooseVideoSource = false
                        val share = profiles.firstOrNull { it.type == ProfileType.WEBDAV.name }
                        if (share != null) browsingShare = share.id else { addPresetType = ProfileType.WEBDAV; showAdd = true }
                    }
                }
            })
        browsingShare?.let { shareId ->
            ShareBrowser(app = app, profileId = shareId, onDismiss = { browsingShare = null }, onPick = { id ->
                browsingShare = null
                pendingVideoPlayId = id
            })
        }
        if (showAdd) AddProfileDialog(initialName = addPresetName, initialType = addPresetType, onDismiss = { showAdd = false; addPresetName = ""; addPresetType = null },
            onPickFile = {
                localImportName = localListName
                localPicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*"))
            },
            onCreate = { name, type, source, username, password, epg ->
            showAdd = false
            addPresetName = ""
            scope.launch {
                runCatching {
                    val id = repo.createProfile(name, type, source, username, password, epg)
                    val loaded = when (type) {
                        ProfileType.M3U_URL, ProfileType.XTREAM -> repo.refreshProfile(id)
                        else -> null
                    }
                    if (type == ProfileType.WEBDAV) browsingShare = id
                    id to loaded
                }.onSuccess { (_, loaded) ->
                    status = if (loaded != null) localizedUiText(R.string.result_channels_loaded, loaded)
                    else UiText.Resource(R.string.result_source_saved)
                }.onFailure { status = UiText.Literal(Redaction.diagnostic(it.message)) }
            }
        })
    }
}

@Composable
private fun LibraryPanel(
    profiles: List<CatalogProfile>,
    channels: List<CatalogChannelRow>,
    onResume: (CatalogChannelRow) -> Unit,
    onFavorites: () -> Unit,
    onTv: () -> Unit,
    onRadio: () -> Unit,
    onVideos: () -> Unit,
    onAdd: () -> Unit,
    onLocal: () -> Unit,
    onVideo: () -> Unit,
    onDemo: () -> Unit,
    onHls: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRefresh: (Long) -> Unit,
    onEpg: (Long) -> Unit,
    onEdit: (CatalogProfile) -> Unit,
    onSchedule: (Long, Boolean, Int) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var profileQuery by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val userProfiles = profiles
    val filteredProfiles = userProfiles.filter { profile ->
        profile.name.contains(profileQuery, ignoreCase = true) ||
            profile.type.replace('_', ' ').contains(profileQuery, ignoreCase = true)
    }
    val recent = remember(channels) {
        channels.filter { it.playedAt != null }.maxByOrNull { it.playedAt ?: 0L }
    }
    val favoriteCount = remember(channels) { channels.count { it.favorite } }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Home already offers resume and quick access; this screen is only about adding and managing lists.
        Text(stringResource(R.string.lib_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.lib_add_content), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onVideo, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.lib_add_video)) }
            OutlinedButton(onClick = onLocal, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.lib_import_m3u)) }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("lib-add-source")) { Text(stringResource(R.string.lib_add_source), style = MaterialTheme.typography.titleMedium) }

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(stringResource(if (showAdvanced) R.string.lib_hide_advanced else R.string.lib_show_advanced))
        }
        if (showAdvanced) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDemo) { Text(stringResource(R.string.lib_load_demo)) }
                OutlinedButton(onClick = onHls) { Text(stringResource(R.string.lib_load_hls)) }
                TextButton(onClick = onImport) { Text(stringResource(R.string.lib_import_backup)) }
                TextButton(onClick = onExport) { Text(stringResource(R.string.lib_export_backup)) }
            }
        }

        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lib_sources), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.lib_sources_count, userProfiles.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showSources = !showSources }) {
                Text(stringResource(if (showSources) R.string.lib_hide_sources else R.string.lib_manage_sources))
            }
        }
        if (showSources) {
            OutlinedTextField(
                profileQuery,
                { profileQuery = it },
                modifier = Modifier.fillMaxWidth().testTag("profile-search"),
                label = { Text(stringResource(R.string.lib_search_source)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            Text(
                stringResource(R.string.lib_sources_manage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (filteredProfiles.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (userProfiles.isEmpty()) R.string.lib_no_sources else R.string.lib_no_match))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filteredProfiles.forEach { profile ->
                        val typeLabel = runCatching { profileTypeLabel(ProfileType.valueOf(profile.type)) }
                            .getOrDefault(profile.type.replace('_', ' '))
                        val localMedia = profile.type == ProfileType.LOCAL_MEDIA.name
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (profile.encryptedSource.isBlank()) Text(stringResource(R.string.lib_source_incomplete), color = MaterialTheme.colorScheme.error)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { onEdit(profile) }, modifier = Modifier.testTag("profile-edit-${profile.id}")) { Text(stringResource(R.string.action_edit)) }
                                    TextButton(onClick = { onDelete(profile.id) }, modifier = Modifier.testTag("profile-delete-${profile.id}")) { Text(stringResource(R.string.action_delete)) }
                                    if (!localMedia) {
                                        TextButton(
                                            onClick = { onRefresh(profile.id) },
                                            modifier = Modifier.testTag("profile-refresh-${profile.id}"),
                                            enabled = profile.encryptedSource.isNotBlank() && profile.type != ProfileType.STALKER.name,
                                        ) { Text(stringResource(R.string.action_refresh)) }
                                        TextButton(
                                            onClick = { onEpg(profile.id) },
                                            modifier = Modifier.testTag("profile-guide-${profile.id}"),
                                            enabled = profile.encryptedEpg.isNotBlank(),
                                        ) { Text(stringResource(R.string.action_guide)) }
                                        TextButton(
                                            onClick = { onSchedule(profile.id, false, 24) },
                                            enabled = profile.encryptedSource.isNotBlank() && profile.type != ProfileType.STALKER.name,
                                        ) { Text(stringResource(R.string.action_every_24h)) }
                                        TextButton(onClick = { onSchedule(profile.id, false, 0) }) { Text(stringResource(R.string.action_auto_stop)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeShortcut(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    glyph: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(glyph, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MorePanel(
    onSources: () -> Unit,
    onGuide: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    onShare: () -> Unit = {},
    onAlarm: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MoreAction(stringResource(R.string.more_sources), stringResource(R.string.more_sources_desc), onSources)
        MoreAction(stringResource(R.string.more_guide), stringResource(R.string.more_guide_desc), onGuide)
        MoreAction(stringResource(R.string.more_services), stringResource(R.string.more_services_desc), onApps)
        MoreAction(stringResource(R.string.alarm_title), stringResource(R.string.alarm_subtitle), onAlarm)
        MoreAction(stringResource(R.string.share_title), stringResource(R.string.share_subtitle), onShare)
        MoreAction(stringResource(R.string.more_settings), stringResource(R.string.more_settings_desc), onSettings)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.more_auto_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.more_auto_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun MoreAction(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun profileTypeLabel(type: ProfileType): String = stringResource(when (type) {
    ProfileType.M3U_URL -> R.string.profile_type_m3u
    ProfileType.LOCAL_M3U -> R.string.profile_type_local_m3u
    ProfileType.LOCAL_MEDIA -> R.string.profile_type_local_media
    ProfileType.XTREAM -> R.string.profile_type_xtream
    ProfileType.MANUAL -> R.string.profile_type_manual
    ProfileType.STALKER -> R.string.profile_type_stalker
    ProfileType.WEBDAV -> R.string.profile_type_webdav
})

@Composable private fun AddProfileDialog(
    initialName: String = "",
    onDismiss: () -> Unit,
    onCreate: (String, ProfileType, String, String, String, String) -> Unit,
    onPickFile: () -> Unit = {},
    initialType: ProfileType? = null,
) {
    // Step 1: say what you have, in plain words. Step 2: only the fields that case needs.
    var type by remember { mutableStateOf(initialType) }
    var name by remember(initialName) { mutableStateOf(initialName) }
    var source by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    var showEpg by remember { mutableStateOf(false) }
    val defaultName = stringResource(when (type) {
        ProfileType.XTREAM -> R.string.add_default_account
        ProfileType.MANUAL -> R.string.add_default_channel
        ProfileType.WEBDAV -> R.string.add_default_share
        else -> R.string.add_default_list
    })
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (type == null) R.string.add_title else R.string.profile_add_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val chosen = type
            if (chosen == null) {
                AddChoice(R.drawable.ic_link, R.string.add_choice_m3u, R.string.add_choice_m3u_desc, "add-m3u") { type = ProfileType.M3U_URL }
                AddChoice(R.drawable.ic_account, R.string.add_choice_xtream, R.string.add_choice_xtream_desc, "add-xtream") { type = ProfileType.XTREAM }
                AddChoice(R.drawable.ic_folder, R.string.add_choice_file, R.string.add_choice_file_desc, "add-file") { onDismiss(); onPickFile() }
                AddChoice(R.drawable.ic_add, R.string.add_choice_single, R.string.add_choice_single_desc, "add-single") { type = ProfileType.MANUAL }
                AddChoice(R.drawable.ic_folder, R.string.add_choice_share, R.string.add_choice_share_desc, "add-share") { type = ProfileType.WEBDAV }
            } else {
                OutlinedTextField(
                    value = source, onValueChange = { source = it }, modifier = Modifier.fillMaxWidth().testTag("add-source-url"),
                    label = { Text(stringResource(when (chosen) {
                        ProfileType.XTREAM -> R.string.field_xtream_url
                        ProfileType.MANUAL -> R.string.field_direct_url
                        ProfileType.WEBDAV -> R.string.field_share_url
                        else -> R.string.field_m3u_url
                    })) },
                    placeholder = { Text(stringResource(when (chosen) {
                        ProfileType.XTREAM -> R.string.add_example_xtream
                        ProfileType.MANUAL -> R.string.add_example_single
                        ProfileType.WEBDAV -> R.string.add_example_share
                        else -> R.string.add_example_m3u
                    })) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                )
                if (chosen == ProfileType.XTREAM || chosen == ProfileType.WEBDAV) {
                    OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.field_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.field_password)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(if (chosen == ProfileType.MANUAL) R.string.add_channel_name else R.string.field_source_name)) },
                    placeholder = { Text(defaultName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (chosen != ProfileType.MANUAL && chosen != ProfileType.WEBDAV) {
                    if (!showEpg) TextButton(onClick = { showEpg = true }) { Text(stringResource(R.string.add_epg_optional)) }
                    else OutlinedTextField(epg, { epg = it }, label = { Text(stringResource(R.string.field_xmltv_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri))
                }
                Text(stringResource(R.string.add_private_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }, confirmButton = {
        val chosen = type
        if (chosen != null) Button(
            onClick = { onCreate(name.ifBlank { defaultName }, chosen, source.trim(), username.trim(), password, epg.trim()) },
            enabled = source.isNotBlank() && (chosen != ProfileType.XTREAM || (username.isNotBlank() && password.isNotBlank())),
            modifier = Modifier.testTag("add-save"),
        ) { Text(stringResource(R.string.add_save)) }
    }, dismissButton = {
        TextButton(onClick = { if (type == null) onDismiss() else type = null }) {
            Text(stringResource(if (type == null) R.string.field_cancel else R.string.add_back))
        }
    })
}

@Composable private fun AddChoice(icon: Int, title: Int, detail: Int, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SlIcon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SlIcon(R.drawable.ic_chevron_right, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChannelPanel(
    channels: List<CatalogChannelRow>,
    dense: Boolean,
    searchLabel: String,
    emptyMessage: String,
    showFilters: Boolean = true,
    addLabel: String? = null,
    onAdd: () -> Unit = {},
    emptyActionLabel: String? = addLabel,
    onEmptyAction: () -> Unit = onAdd,
    onFavorite: (Long) -> Unit,
    onPlay: (CatalogChannelRow) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val allLabel = stringResource(R.string.channel_all)
    var category by remember(searchLabel) { mutableStateOf(allLabel) }
    var recentOnly by remember { mutableStateOf(false) }
    var country by rememberSaveable(searchLabel) { mutableStateOf<String?>(null) }
    var language by rememberSaveable(searchLabel) { mutableStateOf<String?>(null) }
    val categories = remember(channels, allLabel) { listOf(allLabel) + channels.map { it.category }.filter { it.isNotBlank() }.distinct().sorted() }
    val localVideoLabel = stringResource(R.string.profile_type_local_media)
    val uiLocale = LocalConfiguration.current.locales[0]
    val countries = remember(channels, uiLocale) {
        channels.mapNotNull { it.country }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { ChannelOrigin.countryName(it.key, uiLocale) })
            .map { it.key to it.value }
    }
    val languages = remember(channels) { channels.mapNotNull { it.language }.distinct().sorted() }
    val filtered = channels.filter {
        (category == allLabel || it.category == category) &&
            (country == null || it.country == country) &&
            (language == null || it.language == language) &&
            TextMatch.matches(it.name, query) &&
            (!recentOnly || it.playedAt != null)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (channels.isNotEmpty()) {
            OutlinedTextField(
                query,
                { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(searchLabel) },
                singleLine = true,
            )
            if (addLabel != null) {
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("channel-add")) { Text("+ $addLabel") }
            }
        }
        if (showFilters && channels.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) { Text(category); SlIcon(R.drawable.ic_expand_more, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = { category = item; expanded = false })
                        }
                    }
                }
                FilterChip(selected = recentOnly, onClick = { recentOnly = !recentOnly }, label = { Text(stringResource(R.string.channel_recent)) })
            }
            if (countries.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().testTag("channel-country-filter"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = country == null, onClick = { country = null },
                            label = { Text("\uD83C\uDF0D " + stringResource(R.string.channel_all_countries)) })
                    }
                    items(countries, key = { it.first }) { (code, count) ->
                        FilterChip(
                            modifier = Modifier.testTag("channel-country-$code"),
                            selected = country == code,
                            onClick = { country = if (country == code) null else code },
                            label = { Text("${ChannelOrigin.flag(code)} ${ChannelOrigin.countryName(code, uiLocale)} ($count)") },
                        )
                    }
                }
            }
            if (languages.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().testTag("channel-language-filter"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = language == null, onClick = { language = null },
                            label = { Text(stringResource(R.string.channel_all_languages)) })
                    }
                    items(languages, key = { it }) { code ->
                        FilterChip(
                            modifier = Modifier.testTag("channel-language-$code"),
                            selected = language == code,
                            onClick = { language = if (language == code) null else code },
                            label = { Text(ChannelOrigin.languageName(code)) },
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (channels.isEmpty()) emptyMessage else stringResource(R.string.channel_no_results),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (channels.isEmpty() && emptyActionLabel != null) {
                        Button(onClick = onEmptyAction, modifier = Modifier.heightIn(min = 56.dp).testTag("channel-empty-action")) {
                            Text(emptyActionLabel, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 8.dp)) {
                items(filtered, key = { it.id }) { channel ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onPlay(channel) }.padding(if (dense) 8.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, fontWeight = FontWeight.SemiBold)
                                val details = listOfNotNull(
                                    channel.country?.let(ChannelOrigin::flag),
                                    channel.category.takeIf { it.isNotBlank() }?.let { if (it == "Local video") localVideoLabel else it },
                                    channel.language?.let(ChannelOrigin::languageName),
                                ).joinToString("  ·  ")
                                if (details.isNotBlank()) {
                                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onFavorite(channel.id) }, modifier = Modifier.testTag("favorite-${channel.id}")) {
                                SlIcon(if (channel.favorite) R.drawable.ic_nav_favorite else R.drawable.ic_star_outline,
                                    stringResource(if (channel.favorite) R.string.radio_favorite_remove else R.string.radio_favorite_add, channel.name),
                                    tint = if (channel.favorite) Cinema.gold else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun GuidePanel(profiles: List<CatalogProfile>, selected: Long, onSelect: (Long) -> Unit, repo: CatalogRepository) {
    val actual = if (selected == 0L) profiles.firstOrNull()?.id ?: 0 else selected
    val guide by remember(actual) { repo.dao.guide(actual, System.currentTimeMillis()) }.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val allChannels by repo.channels.collectAsState(initial = emptyList())
    val channelNames = remember(allChannels) { allChannels.associate { it.id to it.name } }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Scrolls sideways: with many lists the last ones must stay reachable.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { profiles.forEach { profile -> FilterChip(selected = actual == profile.id, onClick = { onSelect(profile.id) }, label = { Text(profile.name) }) } }
        when {
            profiles.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.guide_no_sources))
            }
            guide.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.guide_no_programs))
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(guide, key = { it.id }) { programme ->
                    val channelName = channelNames[programme.channelId].orEmpty()
                    var reminded by remember(programme.id) { mutableStateOf(ProgrammeReminder.isSet(context, programme.channelId, programme.startMs)) }
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(programme.title, fontWeight = FontWeight.SemiBold)
                                if (channelName.isNotBlank()) Text(channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(programme.startMs))} – ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(programme.endMs))}", style = MaterialTheme.typography.bodySmall)
                            }
                            // A bell only for programmes that have not started yet.
                            if (programme.startMs > System.currentTimeMillis()) {
                                val label = stringResource(if (reminded) R.string.reminder_off else R.string.reminder_on)
                                IconButton(
                                    onClick = {
                                        if (!reminded && !ProgrammeReminder.canNotify(context) && android.os.Build.VERSION.SDK_INT >= 33) {
                                            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        reminded = ProgrammeReminder.toggle(context, programme.channelId, programme.startMs, programme.title, channelName)
                                    },
                                    modifier = Modifier.semantics { contentDescription = label }.testTag("reminder-${programme.id}"),
                                ) { SlIcon(if (reminded) R.drawable.ic_bell else R.drawable.ic_bell_outline, null,
                                    tint = if (reminded) Cinema.gold else MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SettingsPanel(
    prefs: AppPreferences,
    store: Preferences,
    onLanguageChange: (String) -> Unit,
    onUpdate: (suspend () -> Unit) -> Unit,
) {
    val language = StreamLabLocale.selection(LocalContext.current)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("dark" to R.string.settings_dark, "light" to R.string.settings_light, "system" to R.string.settings_system)
                .forEach { (value, labelId) ->
                    FilterChip(
                        selected = prefs.theme == value,
                        onClick = { onUpdate { store.setTheme(value) } },
                        label = { Text(stringResource(labelId)) },
                    )
                }
        }
        SettingSwitch(stringResource(R.string.settings_dense), prefs.dense, tag = "settings-dense") { onUpdate { store.setDense(it) } }
        val saverContext = LocalContext.current
        var dataSaver by remember { mutableStateOf(DataSaverSetting.enabled(saverContext)) }
        SettingSwitch(stringResource(R.string.settings_data_saver), dataSaver, tag = "settings-data-saver") { dataSaver = it; DataSaverSetting.set(saverContext, it) }
        Text(stringResource(R.string.settings_data_saver_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_language_detail), style = MaterialTheme.typography.bodySmall)
        listOf(StreamLabLocale.SYSTEM to R.string.language_system, "fr" to R.string.language_french, "en" to R.string.language_english, "ar" to R.string.language_arabic)
            .forEach { (code, labelId) ->
                FilterChip(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("language-$code"),
                    selected = language == code,
                    onClick = { onLanguageChange(code) },
                    label = { Text(stringResource(labelId), style = MaterialTheme.typography.bodyLarge) },
                )
            }
        HorizontalDivider()
        Text(stringResource(R.string.more_auto_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_car_note), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        var kidsSetup by remember { mutableStateOf(false) }
        Text(stringResource(R.string.kids_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_kids_body), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { kidsSetup = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("settings-kids")) {
            Text(stringResource(R.string.kids_setup_title))
        }
        if (kidsSetup) KidsSetupDialog(onDismiss = { kidsSetup = false })
        HorizontalDivider()
        Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("settings-privacy"))
        Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        PassengerSafetyNotice(Modifier.fillMaxWidth())
        val context = LocalContext.current
        val tipsAgain = stringResource(R.string.settings_tips_done)
        var tipsReset by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { FirstUseTips.showAgain(context); tipsReset = true }, modifier = Modifier.fillMaxWidth().testTag("settings-tips")) {
            SlIcon(R.drawable.ic_help, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_tips))
        }
        if (tipsReset) Text(tipsAgain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text(stringResource(R.string.settings_about_title), style = MaterialTheme.typography.titleMedium)
        val version = remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
        Text(stringResource(R.string.settings_about_version, version), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("settings-version"))
        Text(stringResource(R.string.settings_about_content), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun SettingSwitch(label: String, checked: Boolean, tag: String = "", onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange, modifier = if (tag.isEmpty()) Modifier else Modifier.testTag(tag))
    }
}

/** Shown instead of a black video area when the stream has no picture (radio, audio-only channel). */
@Composable private fun AudioOnlyArtwork(name: String, modifier: Modifier, song: String? = null, logo: androidx.compose.ui.graphics.ImageBitmap? = null) {
    Box(
        modifier.background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF123A35), Color(0xFF0D151A), Color(0xFF2A1E4A))),
        ).testTag("player-audio-artwork"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (logo != null) Surface(Modifier.size(96.dp), shape = RoundedCornerShape(22.dp), color = Color.White) {
                androidx.compose.foundation.Image(logo, null, Modifier.fillMaxSize().padding(6.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            } else MonogramTile(name, 88.dp)
            Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            if (song != null) Text(song, style = MaterialTheme.typography.titleMedium, color = Color(0xFF8EE2C1),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("player-song"))
        }
    }
}

@Composable private fun PlayerPanel(channel: CatalogChannelRow, controller: MediaController?, repo: CatalogRepository, isPip: Boolean, onBack: () -> Unit, onPip: () -> Unit, onStatus: (String) -> Unit, onFullScreenChanged: (Boolean) -> Unit = {}) {
    var aspect by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var source by remember(channel.id) { mutableStateOf<PlaybackSource?>(null) }
    var error by remember { mutableStateOf("") }
    var playerStateId by remember { mutableIntStateOf(R.string.player_state_idle) }
    var tracksKnown by remember { mutableStateOf(false) }
    var hasAudioTrack by remember { mutableStateOf(false) }
    var hasVideoTrack by remember { mutableStateOf(false) }
    var showAlternate by remember { mutableStateOf(false) }
    var alternateUrl by remember { mutableStateOf("") }
    var touchLocked by rememberSaveable(channel.id) { mutableStateOf(false) }
    var fullScreen by rememberSaveable(channel.id) { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(controller?.isPlaying == true) }
    RegisterVideoForPip(hasVideoTrack && isPlaying)
    var onAir by remember(channel.id) { mutableStateOf<String?>(null) }
    // Landscape without system bars while full screen; orientation is only touched around full screen.
    ImmersiveFullScreen(fullScreen)
    DisposableEffect(fullScreen) {
        onFullScreenChanged(fullScreen)
        onDispose { if (fullScreen) onFullScreenChanged(false) }
    }
    BackHandler(enabled = fullScreen) { fullScreen = false }
    val trackSummary = if (tracksKnown) {
        stringResource(
            R.string.player_tracks_summary,
            stringResource(if (hasAudioTrack) R.string.player_yes else R.string.player_no),
            stringResource(if (hasVideoTrack) R.string.player_yes else R.string.player_no),
        )
    } else {
        stringResource(R.string.player_tracks_unknown)
    }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                // The service puts the song in title/artist and the station in subtitle.
                onAir = if (mediaMetadata.subtitle != null && mediaMetadata.title?.toString() != channel.name)
                    listOfNotNull(mediaMetadata.artist?.toString()?.takeIf { it != mediaMetadata.subtitle?.toString() }, mediaMetadata.title?.toString())
                        .joinToString(" - ").takeIf { it.isNotBlank() }
                else null
            }
            override fun onPlayerError(cause: PlaybackException) { error = Redaction.diagnostic(cause.errorCodeName + ": " + cause.message); playerStateId = R.string.player_state_error }
            override fun onPlaybackStateChanged(state: Int) {
                playerStateId = when (state) {
                    Player.STATE_BUFFERING -> R.string.player_state_buffering
                    Player.STATE_READY -> R.string.player_state_ready
                    Player.STATE_ENDED -> R.string.player_state_ended
                    else -> R.string.player_state_idle
                }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val kinds = tracks.groups.map { it.type }
                hasAudioTrack = C.TRACK_TYPE_AUDIO in kinds
                hasVideoTrack = C.TRACK_TYPE_VIDEO in kinds
                tracksKnown = true
            }
        }
        controller?.addListener(listener)
        // Reopening the player mid-song shows the song at once, not at the next title change.
        controller?.let { listener.onMediaMetadataChanged(it.mediaMetadata); isPlaying = it.isPlaying }
        onDispose { controller?.removeListener(listener) }
    }
    val scope = rememberCoroutineScope()
    fun start(fallback: Boolean = false) {
        scope.launch {
            runCatching {
                val loaded = source ?: repo.playbackSource(channel.id).also { source = it }
                val url = if (fallback) loaded.fallbackUrl ?: loaded.url else loaded.url
                val resumePositionMs = repo.resumePositionMs(channel.id)
                val extras = Bundle().apply {
                    putString("media_id", OpaqueMediaId.channel(channel.id))
                    putString("url", url)
                    putString("title", loaded.name)
                    putLong("start_position_ms", resumePositionMs)
                    putBundle("headers", Bundle().apply { loaded.headers.forEach { (k, v) -> putString(k, v) } })
                }
                val activeController = controller ?: error("Player unavailable")
                val request = withContext(android.os.Handler(activeController.applicationLooper).asCoroutineDispatcher()) {
                    activeController.sendCustomCommand(SessionCommand("app.cleanroom.streamlab.PLAY_SOURCE", Bundle.EMPTY), extras)
                }
                val result = withContext(Dispatchers.IO) { request.get() }
                require(result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) { "Playback source rejected" }
            }.onFailure { error = Redaction.diagnostic(it.message) }
        }
    }
    // Live favourite state and logo, so the star and artwork follow what the user does.
    val catalogRows by repo.channels.collectAsState(initial = emptyList())
    val isFavorite = catalogRows.firstOrNull { it.id == channel.id }?.favorite ?: channel.favorite
    val playerContext = LocalContext.current
    val logo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, channel.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val url = repo.logoUrl(channel.id) ?: return@runCatching null
                val bytes = (playerContext.applicationContext as StreamLabApplication).network.fetchMedia(url, 512_000)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.takeIf { it.width >= 48 }?.asImageBitmap()
            }.getOrNull()
        }
    }
    LaunchedEffect(channel.id, controller) {
        val active = controller ?: return@LaunchedEffect
        val alreadyOn = active.currentMediaItem?.mediaId == OpaqueMediaId.channel(channel.id) && active.playbackState != Player.STATE_IDLE
        if (!alreadyOn) start()
    }
    if (isPip) {
        if (controller != null) AndroidView(
            factory = { context -> PlayerView(context).apply { useController = false; player = controller; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
            update = { it.player = controller; it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT },
            modifier = Modifier.fillMaxSize(),
        ) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.player_connecting)) }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val passengerLandscape = maxWidth >= 560.dp && maxWidth > maxHeight
        if (passengerLandscape && !fullScreen) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("player-back")) { SlIcon(R.drawable.ic_arrow_back, stringResource(R.string.player_back)) }
                    Text(channel.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    IconButton(onClick = { scope.launch { repo.toggleFavorite(channel.id) } }, modifier = Modifier.testTag("player-favorite")) {
                        SlIcon(if (isFavorite) R.drawable.ic_nav_favorite else R.drawable.ic_star_outline,
                            stringResource(if (isFavorite) R.string.radio_favorite_remove else R.string.radio_favorite_add, channel.name),
                            tint = if (isFavorite) Cinema.gold else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { touchLocked = true }, modifier = Modifier.testTag("player-lock")) { SlIcon(R.drawable.ic_lock, stringResource(R.string.player_lock)) }
                    if (hasVideoTrack) TextButton(onClick = onPip) { Text("PiP") }
                }
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (tracksKnown && !hasVideoTrack) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            AudioOnlyArtwork(channel.name, Modifier.fillMaxSize(), song = onAir, logo = logo)
                        }
                    } else if (controller != null) Box(Modifier.weight(1f).fillMaxHeight()) {
                        AndroidView(
                            factory = { context -> PlayerView(context).apply { player = controller; useController = true; resizeMode = aspect
                                controllerShowTimeoutMs = 3_500
                                setFullscreenButtonState(false)
                                setFullscreenButtonClickListener { full -> fullScreen = full } } },
                            update = { it.player = controller; it.resizeMode = aspect },
                            modifier = Modifier.fillMaxSize().background(Color.Black).testTag("player-video"),
                        )
                        if (hasVideoTrack) FullScreenToggle(entering = true, onClick = { fullScreen = true }, modifier = Modifier.align(Alignment.TopEnd))
                    } else Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.player_connecting)) }
                    Column(Modifier.widthIn(min = 240.dp, max = 320.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (tracksKnown && !hasVideoTrack) {
                            Button(
                                onClick = { if (isPlaying) controller?.pause() else controller?.play() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("player-toggle"),
                            ) { Text(stringResource(if (isPlaying) R.string.player_pause else R.string.player_play), style = MaterialTheme.typography.titleMedium) }
                        }
                        if (playerStateId == R.string.player_state_buffering) {
                            Text(stringResource(R.string.player_state_buffering), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (error.isNotBlank()) {
                            Text(stringResource(R.string.player_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.player_failed_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { error = ""; start() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.player_retry)) }
                            if (source?.fallbackUrl != null) OutlinedButton(onClick = { error = ""; start(true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.player_alternate_source)) }
                        }
                        CastControls(source = source, category = channel.category, controller = controller, onStatus = onStatus)
                        if (hasVideoTrack) OutlinedButton(
                            onClick = { aspect = if (aspect == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(if (aspect == AspectRatioFrameLayout.RESIZE_MODE_FIT) R.string.player_fit else R.string.player_fill)) }
                        TextButton(onClick = { showAlternate = true }) { Text(stringResource(R.string.player_set_alternate), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        } else if (fullScreen && hasVideoTrack) {
            // Full screen film: only the picture and Media3's own controls (seek bar, tracks, exit).
            if (controller != null) Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { context -> PlayerView(context).apply { player = controller; useController = true; resizeMode = aspect
                        setFullscreenButtonState(true)
                        setFullscreenButtonClickListener { full -> fullScreen = full } } },
                    update = { it.player = controller; it.resizeMode = aspect },
                    modifier = Modifier.fillMaxSize().testTag("player-fullscreen"),
                )
                FullScreenToggle(entering = false, onClick = { fullScreen = false }, modifier = Modifier.align(Alignment.TopStart))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("player-back")) { SlIcon(R.drawable.ic_arrow_back, stringResource(R.string.player_back)) }
                    Text(channel.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    IconButton(onClick = { scope.launch { repo.toggleFavorite(channel.id) } }, modifier = Modifier.testTag("player-favorite")) {
                        SlIcon(if (isFavorite) R.drawable.ic_nav_favorite else R.drawable.ic_star_outline,
                            stringResource(if (isFavorite) R.string.radio_favorite_remove else R.string.radio_favorite_add, channel.name),
                            tint = if (isFavorite) Cinema.gold else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { touchLocked = true }, modifier = Modifier.testTag("player-lock")) { SlIcon(R.drawable.ic_lock, stringResource(R.string.player_lock)) }
                    if (hasVideoTrack) TextButton(onClick = onPip) { Text("PiP") }
                }
                if (tracksKnown && !hasVideoTrack) {
                    AudioOnlyArtwork(channel.name, Modifier.fillMaxWidth().aspectRatio(16f / 9f), song = onAir, logo = logo)
                    Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { if (isPlaying) controller?.pause() else controller?.play() },
                            modifier = Modifier.size(84.dp).testTag("player-toggle"),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Icon(painterResource(if (isPlaying) R.drawable.ic_ctl_pause else R.drawable.ic_ctl_play), contentDescription = null, modifier = Modifier.size(44.dp)) }
                    }
                } else if (controller != null) Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    AndroidView(
                        // Media3's controller: play/pause, seek bar with time, audio and subtitle choice, full screen.
                        factory = { context -> PlayerView(context).apply { player = controller; useController = true; resizeMode = aspect
                            controllerShowTimeoutMs = 3_500
                            setFullscreenButtonState(false)
                            setFullscreenButtonClickListener { full -> fullScreen = full } } },
                        update = { it.player = controller; it.resizeMode = aspect },
                        modifier = Modifier.fillMaxSize().background(Color.Black).testTag("player-video"),
                    )
                    if (hasVideoTrack) FullScreenToggle(entering = true, onClick = { fullScreen = true }, modifier = Modifier.align(Alignment.TopEnd))
                }
                else Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) { Text(stringResource(R.string.player_connecting)) }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (hasVideoTrack) FirstUseTip(FirstUseTips.PLAYER, R.string.tip_player_title,
                        listOf(R.string.tip_player_1, R.string.tip_player_2, R.string.tip_player_3))
                    // Films and videos (not live): watch faster or slower.
                    if (hasVideoTrack && controller?.isCurrentMediaItemLive == false && (controller.duration) > 0) {
                        var speed by remember(channel.id) { mutableFloatStateOf(controller.playbackParameters.speed) }
                        Text(stringResource(R.string.player_speed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                                FilterChip(selected = speed == value, onClick = { speed = value; controller.setPlaybackSpeed(value) },
                                    label = { Text(if (value == 1f) stringResource(R.string.player_speed_normal) else "×" + value.toString().trimEnd('0').trimEnd('.').replace('.', ',')) },
                                    modifier = Modifier.testTag("player-speed-$value"))
                            }
                        }
                    }
                    if (hasVideoTrack) Button(onClick = { fullScreen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("player-fullscreen-button")) {
                        SlIcon(R.drawable.ic_fullscreen, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.player_fullscreen), style = MaterialTheme.typography.titleMedium)
                    }
                    if (playerStateId == R.string.player_state_buffering) {
                        Text(stringResource(R.string.player_state_buffering), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (error.isNotBlank()) {
                        Text(stringResource(R.string.player_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.player_failed_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { error = ""; start() }) { Text(stringResource(R.string.player_retry)) }
                            if (source?.fallbackUrl != null) OutlinedButton(onClick = { error = ""; start(true) }) { Text(stringResource(R.string.player_alternate_source)) }
                        }
                    }
                    CastControls(source = source, category = channel.category, controller = controller, onStatus = onStatus)
                    val externalContext = LocalContext.current
                    if (hasVideoTrack) TextButton(onClick = {
                        scope.launch { runCatching { repo.playbackSource(channel.id) }.getOrNull()?.let { controller?.pause(); ExternalPlayer.open(externalContext, it.url, channel.name) } }
                    }, modifier = Modifier.testTag("player-external")) {
                        SlIcon(R.drawable.ic_open_external, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.external_player))
                    }
                    if (hasVideoTrack) {
                        OutlinedButton(onClick = { aspect = if (aspect == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT }) {
                            Text(stringResource(if (aspect == AspectRatioFrameLayout.RESIZE_MODE_FIT) R.string.player_fit else R.string.player_fill))
                        }
                    }
                    TextButton(onClick = { showAlternate = true }) { Text(stringResource(R.string.player_set_alternate), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (touchLocked) TouchLockOverlay(onUnlock = { touchLocked = false })
    }
    if (showAlternate) AlertDialog(onDismissRequest = { showAlternate = false }, title = { Text(stringResource(R.string.player_alt_title)) }, text = {
        OutlinedTextField(alternateUrl, { alternateUrl = it }, label = { Text(stringResource(R.string.player_alt_url)) })
    }, confirmButton = { Button(onClick = {
        showAlternate = false
        scope.launch { runCatching { repo.setFallback(channel.id, alternateUrl); source = null }
            .onFailure { error = Redaction.diagnostic(it.message) } }
    }) { Text(stringResource(R.string.field_save)) } }, dismissButton = { TextButton(onClick = { showAlternate = false }) { Text(stringResource(R.string.field_cancel)) } })
}

@Composable private fun TrackMenu(controller: MediaController?, type: Int, label: String) {
    var expanded by remember { mutableStateOf(false) }
    val languages = controller?.currentTracks?.groups?.filter { it.type == type }?.flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).language ?: "Unknown" } }?.distinct().orEmpty()
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = languages.isNotEmpty()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { language -> DropdownMenuItem(text = { Text(language) }, onClick = {
                controller?.trackSelectionParameters = controller?.trackSelectionParameters?.buildUpon()?.let { builder ->
                    if (type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguage(language) else builder.setPreferredTextLanguage(language)
                    builder.build()
                } ?: return@DropdownMenuItem
                expanded = false
            }) }
        }
    }
}

/** Always-visible full-screen button on the picture: Media3 only shows its own after a tap. */
@Composable
internal fun FullScreenToggle(entering: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (entering) R.string.player_fullscreen else R.string.player_fullscreen_exit)
    Surface(
        onClick = onClick,
        modifier = modifier.padding(8.dp).size(48.dp).testTag(if (entering) "player-enter-fullscreen" else "player-exit-fullscreen")
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = .55f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SlIcon(if (entering) R.drawable.ic_fullscreen else R.drawable.ic_fullscreen_exit, null, Modifier.size(28.dp), tint = Color.White)
        }
    }
}
