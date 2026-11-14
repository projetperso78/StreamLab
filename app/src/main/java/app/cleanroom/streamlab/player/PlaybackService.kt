package app.cleanroom.streamlab.player

import app.cleanroom.streamlab.core.DataSaver
import app.cleanroom.streamlab.DataSaverSetting
import app.cleanroom.streamlab.core.VoiceMatch
import app.cleanroom.streamlab.R
import app.cleanroom.streamlab.core.SourceFailover
import app.cleanroom.streamlab.core.TextMatch
import android.app.PendingIntent
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.cleanroom.streamlab.NowPlayingWidget
import app.cleanroom.streamlab.core.NowPlaying
import app.cleanroom.streamlab.RadioAlarm
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.AssetDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.cleanroom.streamlab.MainActivity
import app.cleanroom.streamlab.StreamLabApplication
import app.cleanroom.streamlab.core.HeaderPolicy
import app.cleanroom.streamlab.data.PlaybackSource
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.core.SourcePolicy
import app.cleanroom.streamlab.services.RadioStation
import app.cleanroom.streamlab.services.RegionDetector
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var mediaSources: AutoMediaSourceFactory
    private var librarySession: MediaLibrarySession? = null
    private val app get() = application as StreamLabApplication

    @Volatile private var radioCountriesCache: Pair<Long, List<MediaItem>>? = null
    private val radioStationLists = ConcurrentHashMap<String, Pair<Long, List<RadioStation>>>()
    private val radioStationByMediaId = ConcurrentHashMap<String, RadioStation>()
    @Volatile private var zapIds: List<String> = emptyList()
    @Volatile private var zapCurrent: String? = null
    private var sleepJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        mediaSources = AutoMediaSourceFactory(this) { channelId -> app.repository.playbackSource(channelId) }
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSources)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true).build()
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val zapPlayer = ZapPlayer(player)
        librarySession = MediaLibrarySession.Builder(this, zapPlayer, Callback(player))
            .setSessionActivity(pending).build()
        watchDataSaver(player)
        if (android.util.Log.isLoggable("StreamLabIcy", android.util.Log.DEBUG)) player.addAnalyticsListener(androidx.media3.exoplayer.util.EventLogger("StreamLabIcy"))
        player.addListener(object : Player.Listener {
            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is androidx.media3.extractor.metadata.icy.IcyInfo) {
                        zapPlayer.setSong(NowPlaying.songLine(entry.title, player.currentMediaItem?.mediaMetadata?.title?.toString()))
                    }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                zapPlayer.setSong(null)
                // A new item starts from its main address again.
                mediaItem?.mediaId?.let { id -> failoverIndex.keys.retainAll(setOf(id)) }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failOver(player) }
        })
        librarySession?.player?.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    NowPlayingWidget.publish(this@PlaybackService, p.mediaMetadata.title ?: p.currentMediaItem?.mediaMetadata?.title, p.isPlaying)
                }
            }
        })
        serviceScope.launch {
            app.repository.channels.collectLatest { channels ->
                val validIds = channels.mapTo(HashSet()) { it.id }
                withContext(Dispatchers.Main) {
                    val activePlayer = librarySession?.player ?: return@withContext
                    val currentId = OpaqueMediaId.channelId(activePlayer.currentMediaItem?.mediaId)
                    if (currentId != null && currentId !in validIds) {
                        activePlayer.stop()
                        activePlayer.clearMediaItems()
                        android.util.Log.i("StreamLabPlayback", "Cleared media item removed from catalog")
                    }
                }
            }
        }
        serviceScope.launch {
            while (true) {
                delay(PROGRESS_SAVE_MS)
                val snapshot = withContext(Dispatchers.Main) {
                    val activePlayer = librarySession?.player ?: return@withContext null
                    val id = OpaqueMediaId.channelId(activePlayer.currentMediaItem?.mediaId) ?: return@withContext null
                    val duration = activePlayer.duration
                    // Live radio/TV can expose a short replay window: that is not a film to resume.
                    if (activePlayer.isCurrentMediaItemLive || activePlayer.isCurrentMediaItemDynamic) return@withContext null
                    if (!activePlayer.isCurrentMediaItemSeekable || duration <= 0L || duration == C.TIME_UNSET) return@withContext null
                    Triple(id, activePlayer.currentPosition.coerceAtLeast(0L), duration)
                }
                if (snapshot != null) {
                    runCatching { app.repository.updatePlaybackProgress(snapshot.first, snapshot.second, snapshot.third) }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Alarm while something is already loaded: empty the player so "play" goes through
        // playback resumption, which starts the alarm station instead of the current item.
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON && RadioAlarm.hasPending(this)) {
            librarySession?.player?.run { stop(); clearMediaItems() }
        }
        if (intent?.action != "android.media.action.MEDIA_PLAY_FROM_SEARCH")
            return super.onStartCommand(intent, flags, startId)
        val query = intent.getStringExtra(SearchManager.QUERY).orEmpty().take(100)
        serviceScope.launch {
            try {
                val current = catalog()
                val target = voiceSearch(query, current) ?: return@launch
                val (item, source, id) = resolveForPlayback(target, current) ?: return@launch
                withContext(Dispatchers.Main) {
                    val player = librarySession?.player ?: return@withContext
                    mediaSources.put(item.mediaId, source)
                    // Android Auto cannot attach a video surface (see onConnectAsync), but the
                    // shared phone/passenger controller must keep the video track available.
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
                    player.setMediaItem(item.buildUpon().setUri("streamlab://${item.mediaId}").build())
                    player.prepare()
                    player.play()
                }
                runCatching { app.repository.markPlayed(id) }
            } catch (failure: Exception) {
                android.util.Log.w("StreamLabPlayback", "Search playback unavailable: ${failure.javaClass.simpleName}")
            }
        }
        return START_NOT_STICKY
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var dataSaverListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile private var onMeteredNetwork = false

    /** "Économie de données": caps video on mobile data, follows network changes and the setting. */
    private fun watchDataSaver(player: Player) {
        fun apply() = android.os.Handler(mainLooper).post {
            val limits = DataSaver.limits(DataSaverSetting.enabled(this), onMeteredNetwork)
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setMaxVideoBitrate(limits.maxVideoBitrate)
                .setMaxVideoSize(limits.maxVideoWidth, limits.maxVideoHeight)
                .build()
        }
        val manager = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                val metered = !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                if (metered != onMeteredNetwork) { onMeteredNetwork = metered; apply() }
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> apply() }
        DataSaverSetting.prefs(this).registerOnSharedPreferenceChangeListener(listener)
        dataSaverListener = listener
        apply()
    }

    override fun onDestroy() {
        networkCallback?.let { cb -> runCatching { getSystemService(android.net.ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
        dataSaverListener?.let { DataSaverSetting.prefs(this).unregisterOnSharedPreferenceChangeListener(it) }
        NowPlayingWidget.publish(this, NowPlayingWidget.state(this).first, false)
        serviceScope.cancel()
        librarySession?.run { player.release(); release() }
        librarySession = null
        super.onDestroy()
    }

    private suspend fun catalog() = AutoLibrary(app.repository.profiles.first(), app.repository.channels.first())

    private fun AutoLibrary.Node.mediaItem(artwork: String? = null, subtitle: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title)
            .setStation(if (playable) title else null)
            .setSubtitle(subtitle)
            .setArtworkUri(artwork?.let(Uri::parse))
            .setIsBrowsable(browsable)
            .setIsPlayable(playable)
            .build())
        .build()

    /** Resolves a car-visible media id to a playable item, its private source and its catalog id. */
    private suspend fun resolveForPlayback(mediaId: String, current: AutoLibrary): Triple<MediaItem, PlaybackSource, Long>? {
        if (mediaId.startsWith(RADIO_STATION_PREFIX)) {
            val parsed = parseRadioStationId(mediaId) ?: return null
            val station = resolveRadioStation(mediaId) ?: return null
            val id = app.repository.addRadioStation(
                countryCode = station.countryCode.ifBlank { parsed.first },
                name = station.name,
                url = station.streamUrl,
                favicon = station.favicon,
                stationUuid = station.uuid,
                backups = station.backups,
            )
            val source = runCatching { app.repository.playbackSource(id) }.getOrNull() ?: return null
            val node = AutoLibrary.Node(OpaqueMediaId.channel(id), AutoLibrary.safeTitle(station.name, "Radio"), browsable = false, playable = true)
            return Triple(node.mediaItem(station.favicon, AutoLibrary.countryTitle(parsed.first)), source, id)
        }
        val id = current.channelId(mediaId) ?: return null
        val source = runCatching { app.repository.playbackSource(id) }.getOrNull() ?: return null
        val node = current.item(mediaId) ?: return null
        return Triple(node.mediaItem(), source, id)
    }

    /** Remembers the list the item was picked from so the steering-wheel skip buttons zap through it. */
    private suspend fun rememberZapList(mediaId: String, current: AutoLibrary) {
        val ids = if (mediaId.startsWith(RADIO_STATION_PREFIX)) {
            parseRadioStationId(mediaId)?.let { (code, _) ->
                runCatching { onlineRadioStations(code) }.getOrDefault(emptyList())
                    .mapNotNull { radioStationMediaId(code, it.uuid) }
            }.orEmpty()
        } else current.siblings(mediaId)
        zapIds = ids
        zapCurrent = mediaId
    }

    private fun zap(delta: Int) {
        val target = ZapList.step(zapIds, zapCurrent, delta) ?: return
        serviceScope.launch {
            val resolved = runCatching { resolveForPlayback(target, catalog()) }.getOrNull() ?: return@launch
            val (item, source, id) = resolved
            withContext(Dispatchers.Main) {
                val player = (librarySession?.player as? ForwardingPlayer)?.wrappedPlayer ?: return@withContext
                mediaSources.put(item.mediaId, source)
                player.setMediaItem(item.buildUpon().setUri("streamlab://${item.mediaId}").build())
                player.prepare()
                player.play()
                zapCurrent = target
            }
            runCatching { app.repository.markPlayed(id) }
        }
    }

    /** Always offers next/previous to car controllers; they zap within the chosen list. */
    private inner class ZapPlayer(player: Player) : ForwardingPlayer(player) {
        private val zapCommands = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*zapCommands).build()
        override fun isCommandAvailable(command: Int): Boolean = command in zapCommands || super.isCommandAvailable(command)
        // A queued playlist (favourite radios in rotation) moves through its own items, looping;
        // a single channel zaps through the list it was picked from.
        private fun inPlaylist() = wrappedPlayer.mediaItemCount > 1
        override fun seekToNext() { if (inPlaylist()) wrappedPlayer.seekToNextMediaItem() else zap(1) }
        override fun seekToNextMediaItem() { if (inPlaylist()) wrappedPlayer.seekToNextMediaItem() else zap(1) }
        override fun seekToPrevious() { if (inPlaylist()) wrappedPlayer.seekToPreviousMediaItem() else zap(-1) }
        override fun seekToPreviousMediaItem() { if (inPlaylist()) wrappedPlayer.seekToPreviousMediaItem() else zap(-1) }
        override fun hasNextMediaItem(): Boolean = zapIds.size > 1 || super.hasNextMediaItem()
        override fun hasPreviousMediaItem(): Boolean = zapIds.size > 1 || super.hasPreviousMediaItem()

        // Car screens print the error text in large letters: give a short sentence, never codes.
        override fun getPlayerError(): androidx.media3.common.PlaybackException? {
            val error = super.getPlayerError() ?: return null
            return androidx.media3.common.PlaybackException(getString(R.string.auto_unavailable), null, error.errorCode)
        }

        // Song currently on air (radio ICY metadata). ExoPlayer keeps the station name as title, so
        // the song is merged here and pushed to every controller: phone, Android Auto, widget.
        private val listeners = java.util.concurrent.CopyOnWriteArrayList<Player.Listener>()
        @Volatile private var song: String? = null
        override fun addListener(listener: Player.Listener) { super.addListener(listener); listeners += listener }
        override fun removeListener(listener: Player.Listener) { super.removeListener(listener); listeners -= listener }
        override fun getMediaMetadata(): MediaMetadata {
            // The stream's own "icy-name" header (often a file name such as "franceinfo-hifi.aac")
            // would replace the station: keep the clean name from the catalogue instead.
            val own = currentMediaItem?.mediaMetadata
            val raw = super.getMediaMetadata()
            val clean = own?.station ?: own?.title
            val base = if (clean != null) raw.buildUpon().setStation(clean).setSubtitle(own?.subtitle).build() else raw
            val onAir = song ?: return base
            val station = base.title?.toString()
            val (artist, title) = NowPlaying.split(onAir)
            return base.buildUpon().setTitle(title).setArtist(artist ?: station).setSubtitle(station).setStation(station).build()
        }
        // Listeners hear the stream's raw metadata first (forwarded as is): send the clean version
        // right after, so the phone, the car and the widget all end on the catalogue name.
        private val main = android.os.Handler(android.os.Looper.getMainLooper())
        init {
            wrappedPlayer.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { main.post { pushMetadata() } }
            })
        }
        fun setSong(value: String?) {
            if (value == song) return
            song = value
            pushMetadata()
        }
        private fun pushMetadata() {
            val metadata = mediaMetadata
            val events = Player.Events(androidx.media3.common.FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())
            listeners.forEach { it.onMediaMetadataChanged(metadata); it.onEvents(this, events) }
        }
    }

    private suspend fun onlineRadioCountries(): List<MediaItem> {
        val now = System.currentTimeMillis()
        radioCountriesCache?.takeIf { now - it.first < RADIO_CACHE_MS }?.let { return it.second }
        val items = app.radioDirectory.countries().map { country ->
            MediaItem.Builder()
                .setMediaId(AutoLibrary.radioCountryId(country.code))
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(AutoLibrary.countryTitle(country.code))
                    .setSubtitle("${country.stationCount} radios")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build())
                .build()
        }
        radioCountriesCache = now to items
        return items
    }

    /**
     * "Ok Google, mets RTL": a saved station first; otherwise the best match in the radio
     * directory of the listener's country, so a station never played before still starts.
     */
    private suspend fun voiceSearch(query: String, current: AutoLibrary): String? {
        val spoken = VoiceMatch.clean(query)
        android.util.Log.i("StreamLabVoice", "voice request (${spoken.length} chars)")
        if (spoken.isBlank()) return current.search("").firstOrNull()?.id
        current.search(spoken).let { saved ->
            val index = VoiceMatch.best(saved.map { it.title }, spoken)
            if (index >= 0) return saved[index].id
        }
        val country = RegionDetector.detect(this) ?: return null
        val stations = runCatching { onlineRadioStations(country) }.getOrNull().orEmpty()
        val index = VoiceMatch.best(stations.map { it.name }, spoken)
        android.util.Log.i("StreamLabVoice", "directory $country: ${stations.size} stations, match=$index")
        return stations.getOrNull(index)?.let { radioStationMediaId(country, it.uuid) }
    }

    private suspend fun onlineRadioStations(countryCode: String): List<RadioStation> {
        val code = countryCode.uppercase()
        val now = System.currentTimeMillis()
        radioStationLists[code]?.takeIf { now - it.first < RADIO_CACHE_MS }?.let { return it.second }
        val stations = app.radioDirectory.stations(code, limit = 250)
        radioStationLists[code] = now to stations
        stations.forEach { station ->
            radioStationMediaId(code, station.uuid)?.let { mediaId -> radioStationByMediaId[mediaId] = station }
        }
        return stations
    }

    private suspend fun searchLibraryItems(query: String): List<MediaItem> {
        val term = query.trim().take(100)
        val current = catalog()
        val local = current.search(term).map { it.mediaItem() }
        if (term.isEmpty()) return local

        val exactCountry = runCatching { onlineRadioCountries() }
            .getOrDefault(emptyList())
            .firstOrNull { item ->
                val code = AutoLibrary.countryCode(item.mediaId) ?: return@firstOrNull false
                code.equals(term, ignoreCase = true) ||
                    AutoLibrary.countryName(code).equals(term, ignoreCase = true)
            }

        if (exactCountry != null) {
            val code = requireNotNull(AutoLibrary.countryCode(exactCountry.mediaId))
            val stations = runCatching {
                onlineRadioStations(code).take(50).mapNotNull { it.mediaItem(code) }
            }.onFailure {
                android.util.Log.w("StreamLabPlayback", "Radio search stations unavailable: ${it.javaClass.simpleName}")
            }.getOrDefault(emptyList())
            return (listOf(exactCountry) + stations + local)
                .distinctBy { it.mediaId }
                .take(100)
        }

        val cachedStations = radioStationLists.values.asSequence()
            .flatMap { it.second.asSequence() }
            .filter { station ->
                TextMatch.matches(station.name, term) ||
                    TextMatch.matches(station.tags, term)
            }
            .mapNotNull { station -> station.mediaItem(station.countryCode) }
            .toList()
        return (cachedStations + local).distinctBy { it.mediaId }.take(100)
    }

    private fun RadioStation.mediaItem(countryCode: String): MediaItem? {
        val mediaId = radioStationMediaId(countryCode, uuid) ?: return null
        val details = listOfNotNull(
            codec?.takeIf { it.isNotBlank() },
            bitrate.takeIf { it > 0 }?.let { "$it kb/s" },
        ).joinToString(" · ")
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(AutoLibrary.safeTitle(name, "Radio"))
                .setSubtitle(details.ifBlank { AutoLibrary.countryName(countryCode) })
                .setArtworkUri(favicon?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build())
            .build()
    }

    private suspend fun resolveRadioStation(mediaId: String): RadioStation? {
        radioStationByMediaId[mediaId]?.let { return it }
        val parsed = parseRadioStationId(mediaId) ?: return null
        val station = onlineRadioStations(parsed.first).firstOrNull { it.uuid == parsed.second } ?: return null
        radioStationByMediaId[mediaId] = station
        return station
    }

    private fun radioStationMediaId(countryCode: String, uuid: String): String? {
        val code = countryCode.uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) } ?: return null
        val safeUuid = uuid.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) } ?: return null
        return "$RADIO_STATION_PREFIX$code:$safeUuid"
    }

    private fun parseRadioStationId(mediaId: String): Pair<String, String>? {
        if (!mediaId.startsWith(RADIO_STATION_PREFIX)) return null
        val parts = mediaId.removePrefix(RADIO_STATION_PREFIX).split(':', limit = 2)
        val code = parts.getOrNull(0)?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{2}")) } ?: return null
        val uuid = parts.getOrNull(1)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) } ?: return null
        return code to uuid
    }

    private fun <T> asyncValue(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        serviceScope.launch {
            try { result.set(block()) }
            catch (failure: Exception) { result.setException(failure) }
        }
        return result
    }

    private inner class Callback(private val player: ExoPlayer) : MediaLibrarySession.Callback {
        private fun isTrustedInteractiveController(controller: MediaSession.ControllerInfo): Boolean {
            if (ControllerPolicy.canPlaySource(controller.packageName, controller.uid, packageName, applicationInfo.uid)) return true
            val signaturesMatch = runCatching {
                packageManager.checkSignatures(packageName, controller.packageName) == PackageManager.SIGNATURE_MATCH
            }.getOrDefault(false)
            return ControllerPolicy.isTrustedPassengerCompanion(controller.packageName, packageName, signaturesMatch)
        }

        override fun onConnectAsync(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.ConnectionResult> {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().apply {
                if (isTrustedInteractiveController(controller)) {
                    add(SessionCommand(PLAY_SOURCE, Bundle.EMPTY))
                    add(SessionCommand(SleepTimer.COMMAND, Bundle.EMPTY))
                }
            }.build()
            val playerCommands = if (isTrustedInteractiveController(controller)) MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                else MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .remove(Player.COMMAND_SET_VIDEO_SURFACE)
                    .remove(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS).build()
            return Futures.immediateFuture(MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .setAvailablePlayerCommands(playerCommands).build())
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(AutoLibrary(emptyList(), emptyList()).root().mediaItem(), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            asyncValue {
                if (page < 0 || pageSize < 1) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                else {
                    val current = catalog()
                    val items: List<MediaItem> = when {
                        parentId == AutoLibrary.RADIO -> {
                            val local = current.children(parentId).map { it.mediaItem() }
                            val online = runCatching { onlineRadioCountries() }
                                .onFailure { android.util.Log.w("StreamLabPlayback", "Online radio countries unavailable: ${it.javaClass.simpleName}") }
                                .getOrDefault(emptyList())
                            val near = RegionDetector.detect(this@PlaybackService)
                            // Sort by the country name the driver reads (after the flag), not by ISO code.
                            val collator = java.text.Collator.getInstance()
                            val sorted = (online + local).distinctBy { it.mediaId }
                                .sortedWith(compareBy(collator) { it.mediaMetadata.title?.toString().orEmpty().dropWhile { c -> !c.isLetter() } })
                            ZapList.nearFirst(sorted) { near != null && AutoLibrary.countryCode(it.mediaId) == near }.map { item ->
                                if (near == null || AutoLibrary.countryCode(item.mediaId) != near) item
                                else item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
                                    .setTitle("\uD83D\uDCCD ${item.mediaMetadata.title}").build()).build()
                            }
                        }
                        AutoLibrary.countryCode(parentId) != null -> {
                            val code = requireNotNull(AutoLibrary.countryCode(parentId))
                            val local = current.children(parentId).map { it.mediaItem() }
                            val online = runCatching { onlineRadioStations(code).mapNotNull { it.mediaItem(code) } }
                                .onFailure { android.util.Log.w("StreamLabPlayback", "Online radio stations unavailable: ${it.javaClass.simpleName}") }
                                .getOrDefault(emptyList())
                            (local + online).distinctBy { it.mediaMetadata.title?.toString()?.trim()?.lowercase().orEmpty() }
                        }
                        else -> current.children(parentId).map { it.mediaItem() }
                    }
                    val start = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
                    LibraryResult.ofItemList(items.drop(start).take(pageSize), params)
                }
            }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> =
            asyncValue {
                when {
                    AutoLibrary.countryCode(mediaId) != null -> LibraryResult.ofItem(
                        AutoLibrary.Node(
                            mediaId,
                            AutoLibrary.countryTitle(requireNotNull(AutoLibrary.countryCode(mediaId))),
                            browsable = true,
                            playable = false,
                        ).mediaItem(),
                        null,
                    )
                    mediaId.startsWith(RADIO_STATION_PREFIX) -> {
                        val parsed = parseRadioStationId(mediaId)
                        val station = resolveRadioStation(mediaId)
                        val item = if (parsed != null && station != null) station.mediaItem(parsed.first) else null
                        if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                        else LibraryResult.ofItem(item, null)
                    }
                    else -> {
                        val item = catalog().item(mediaId)
                        if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                        else LibraryResult.ofItem(item.mediaItem(), null)
                    }
                }
            }

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> =
            asyncValue {
                val count = searchLibraryItems(query).size
                withContext(Dispatchers.Main) { session.notifySearchResultChanged(browser, query, count, params) }
                LibraryResult.ofVoid(params)
            }

        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            asyncValue {
                if (page < 0 || pageSize < 1) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                else {
                    val matches = searchLibraryItems(query)
                    val start = (page.toLong() * pageSize).coerceAtMost(matches.size.toLong()).toInt()
                    LibraryResult.ofItemList(matches.drop(start).take(pageSize), params)
                }
            }

        override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> =
            asyncValue {
                if (mediaItems.size !in 1..100) return@asyncValue emptyList()
                val current = catalog()
                val allowStaleResumeFallback = mediaItems.size == 1 && !isTrustedInteractiveController(controller)
                val resolved = mediaItems.map { item ->
                    var mediaId = if (item.mediaId.isNotBlank()) item.mediaId else
                        item.requestMetadata.searchQuery?.let { voiceSearch(it, current) }
                            ?: return@asyncValue emptyList()

                    val requestedChannelId = OpaqueMediaId.channelId(mediaId)
                    if (!mediaId.startsWith(RADIO_STATION_PREFIX) && current.channelId(mediaId) == null &&
                        allowStaleResumeFallback && requestedChannelId != null) {
                        // Only a genuinely deleted channel is stale. If the channel still exists in Room
                        // but is absent from the browsable catalog, reject it instead of redirecting it.
                        if (app.repository.dao.channel(requestedChannelId) != null) return@asyncValue emptyList()
                        mediaId = app.repository.resumeMediaId() ?: return@asyncValue emptyList()
                        android.util.Log.i("StreamLabPlayback", "Stale external media id fell back to current resume item")
                    }
                    val playable = resolveForPlayback(mediaId, current) ?: return@asyncValue emptyList()
                    mediaId to playable
                }
                if (resolved.size == 1) rememberZapList(resolved.single().first, current)
                if (resolved.isEmpty()) return@asyncValue emptyList()
                withContext(Dispatchers.Main) {
                    // Keep video available to the phone/passenger PlayerView. External car controllers
                    // still cannot attach a video surface because COMMAND_SET_VIDEO_SURFACE is removed.
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
                    resolved.forEach { (_, r) -> mediaSources.put(r.first.mediaId, r.second) }
                }
                resolved.forEach { (_, r) -> runCatching { app.repository.markPlayed(r.third) } }
                resolved.map { (_, r) ->
                    r.first.buildUpon().setUri("streamlab://${r.first.mediaId}").build()
                }
            }

        private fun playbackResumption(controller: MediaSession.ControllerInfo, forPlayback: Boolean = true): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            asyncValue {
                // A ringing radio alarm picks its own station, if it still exists in the catalog.
                val alarmId = if (forPlayback) RadioAlarm.takePending(this@PlaybackService)?.takeIf { it.isNotBlank() } else null
                val mediaId = alarmId?.takeIf { catalog().item(it) != null }
                    ?: app.repository.resumeMediaId()
                    ?: throw IllegalStateException("No recent channel available")
                val id = OpaqueMediaId.channelId(mediaId)
                    ?: throw IllegalStateException("Invalid resume media id")
                val current = catalog()
                val node = current.item(mediaId)
                    ?: throw IllegalStateException("Recent channel is no longer available")
                val source = app.repository.playbackSource(id)
                withContext(Dispatchers.Main) {
                    mediaSources.put(node.id, source)
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
                }
                MediaSession.MediaItemsWithStartPosition(
                    listOf(node.mediaItem().buildUpon().setUri("streamlab://${node.id}").build()),
                    0,
                    app.repository.resumePositionMs(id),
                )
            }

        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            playbackResumption(controller)

        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            playbackResumption(controller, isForPlayback)

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (isTrustedInteractiveController(controller) && customCommand.customAction == SleepTimer.COMMAND) {
                return try {
                    val delayMs = SleepTimer.delayMs(args.getInt(SleepTimer.ARG_MINUTES, 0))
                    sleepJob?.cancel()
                    sleepJob = delayMs?.let { wait ->
                        serviceScope.launch {
                            delay(wait)
                            withContext(Dispatchers.Main) { player.pause() }
                        }
                    }
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } catch (_: IllegalArgumentException) {
                    Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                }
            }
            if (!isTrustedInteractiveController(controller) || customCommand.customAction != PLAY_SOURCE)
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            return try {
                val publicMediaId = args.getString("media_id")
                    ?.takeIf { OpaqueMediaId.channelId(it) != null }
                    ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                val raw = args.getString("url") ?: ""
                val url = when {
                    raw == "fixture://demo.mp4" -> raw
                    raw.startsWith("content://", ignoreCase = true) -> SourcePolicy.requireLocalContent(raw)
                    else -> SourcePolicy.requireMediaRemote(raw)
                }
                val headerBundle = args.getBundle("headers")
                val headers = headerBundle?.keySet()?.associateWith { headerBundle.getString(it).orEmpty() }.orEmpty()
                val mediaSource = when {
                    url == "fixture://demo.mp4" -> {
                        ProgressiveMediaSource.Factory { AssetDataSource(this@PlaybackService) }
                            .createMediaSource(MediaItem.fromUri("asset:///demo.mp4"))
                    }
                    url.startsWith("content://", ignoreCase = true) -> {
                        ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this@PlaybackService))
                            .createMediaSource(MediaItem.fromUri(url))
                    }
                    else -> {
                        val factory = OkHttpDataSource.Factory(app.network.playerClient())
                            .setDefaultRequestProperties(HeaderPolicy.sanitize(headers))
                        val item = MediaItem.fromUri(url)
                        if (url.substringBefore('?').endsWith(".m3u8", true))
                            HlsMediaSource.Factory(factory).createMediaSource(item)
                        else ProgressiveMediaSource.Factory(factory).createMediaSource(item)
                    }
                }
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
                val publicItem = MediaItem.Builder().setMediaId(publicMediaId)
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(AutoLibrary.safeTitle(args.getString("title").orEmpty(), "Channel"))
                        .setIsPlayable(true).setIsBrowsable(false).build()).build()
                val startPositionMs = args.getLong("start_position_ms", 0L).coerceAtLeast(0L)
                failoverIndex.remove(publicMediaId)
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.setMediaSource(mediaSources.mask(mediaSource, publicItem), startPositionMs)
                player.prepare()
                player.play()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } catch (failure: Exception) {
                android.util.Log.w("StreamLabPlayback", "Playback request failed: ${failure.javaClass.simpleName}")
                Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
            }
        }
    }

    /** Index, in the channel's address list, of the address currently tried. */
    private val failoverIndex = HashMap<String, Int>()

    /**
     * A source failed: move to the channel's next address (three at most in all), so a dead
     * stream switches by itself to a working copy on the phone, in the car and on the widget.
     */
    private fun failOver(player: Player) {
        val item = player.currentMediaItem ?: return
        val channelId = OpaqueMediaId.channelId(item.mediaId) ?: return
        val failed = failoverIndex[item.mediaId] ?: 0
        serviceScope.launch {
            val source = runCatching { app.repository.playbackSource(channelId) }.getOrNull() ?: return@launch
            val urls = source.allUrls
            val next = SourceFailover.nextIndex(failed, urls.size) ?: run {
                // Every address of this station failed: in a playlist, move on to the next station.
                withContext(Dispatchers.Main) {
                    if (player.currentMediaItem?.mediaId == item.mediaId && player.mediaItemCount > 1) {
                        android.util.Log.i("StreamLabPlayback", "Station down, next in playlist")
                        player.seekToNextMediaItem()
                        player.prepare()
                        player.play()
                    }
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (player.currentMediaItem?.mediaId != item.mediaId) return@withContext
                failoverIndex[item.mediaId] = next
                mediaSources.put(item.mediaId, source.copy(url = urls[next]))
                android.util.Log.i("StreamLabPlayback", "Source ${failed + 1} failed, trying ${next + 1} of ${urls.size}")
                player.setMediaItem(item.buildUpon().setUri("streamlab://${item.mediaId}").build())
                player.prepare()
                player.play()
            }
        }
    }

    companion object {
        const val PLAY_SOURCE = "app.cleanroom.streamlab.PLAY_SOURCE"
        private const val RADIO_STATION_PREFIX = "radio-station:"
        private const val RADIO_CACHE_MS = 10 * 60 * 1000L
        private const val PROGRESS_SAVE_MS = 5_000L
    }
}
