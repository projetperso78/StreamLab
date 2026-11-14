package app.cleanroom.streamlab.data

import app.cleanroom.streamlab.core.SourceFailover
import app.cleanroom.streamlab.core.ChannelClassifier
import app.cleanroom.streamlab.core.ShareCode
import kotlinx.coroutines.flow.first
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.cleanroom.streamlab.core.GuidePayload
import app.cleanroom.streamlab.core.M3uParser
import app.cleanroom.streamlab.core.FetchGateway
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.core.ParsedChannel
import app.cleanroom.streamlab.core.SourcePolicy
import app.cleanroom.streamlab.core.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.net.URLEncoder

private const val RETIRED_BUNDLED_PROFILE = "TV France - Direct"

class CatalogRepository(private val context: Context, private val database: StreamLabDatabase, private val secrets: Secrets, private val network: FetchGateway, private val preferences: Preferences? = null) {
    val dao = database.catalog()
    val profiles = dao.profiles()
    val channels = dao.channels()
    /** Films and videos started but not finished, newest first (filtered by ContinueWatching). */
    val inProgress = dao.inProgress()

    suspend fun createProfile(name: String, type: ProfileType, source: String, username: String = "", password: String = "", epg: String = ""): Long = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && name.length <= 80) { "Enter a profile name" }
        if (type == ProfileType.WEBDAV) allowHomeServer(source)
        when (type) {
            ProfileType.M3U_URL, ProfileType.XTREAM, ProfileType.MANUAL, ProfileType.WEBDAV -> SourcePolicy.requireMediaRemote(source)
            ProfileType.STALKER -> SourcePolicy.requireRemote(source)
            ProfileType.LOCAL_M3U, ProfileType.LOCAL_MEDIA -> SourcePolicy.requireLocalContent(source)
        }
        if (epg.isNotBlank()) SourcePolicy.requireRemote(epg)
        val credentials = JSONObject().put("username", username).put("password", password).toString()
        val id = dao.saveProfile(ProfileEntity(name = name.trim(), type = type.name, encryptedSource = secrets.encrypt(source), encryptedCredentials = secrets.encrypt(credentials), encryptedEpg = secrets.encrypt(epg)))
        dao.saveSettings(ProfileSettingsEntity(id))
        if (type == ProfileType.MANUAL || type == ProfileType.LOCAL_MEDIA) {
            replaceChannels(
                id,
                listOf(ParsedChannel(name.trim(), source, if (type == ProfileType.LOCAL_MEDIA) "Local video" else "Manual", null, null, null, emptyMap())),
            )
        }
        id
    }

    suspend fun updateProfile(id: Long, name: String, source: String, username: String, password: String, epg: String) = withContext(Dispatchers.IO) {
        val current = dao.profile(id) ?: throw IllegalArgumentException("Profile missing")
        require(name.isNotBlank() && name.length <= 80) { "Enter a profile name" }
        val type = ProfileType.valueOf(current.type)
        if (source.isNotBlank()) {
            when (type) {
                ProfileType.LOCAL_M3U, ProfileType.LOCAL_MEDIA -> SourcePolicy.requireLocalContent(source)
                ProfileType.M3U_URL, ProfileType.XTREAM, ProfileType.MANUAL -> SourcePolicy.requireMediaRemote(source)
                ProfileType.WEBDAV -> { allowHomeServer(source); SourcePolicy.requireMediaRemote(source) }
                ProfileType.STALKER -> SourcePolicy.requireRemote(source)
            }
        }
        if (epg.isNotBlank()) SourcePolicy.requireRemote(epg)
        val oldCredentials = JSONObject(secrets.decrypt(current.encryptedCredentials).ifBlank { "{}" })
        val nextCredentials = JSONObject().put("username", username.ifBlank { oldCredentials.optString("username") })
            .put("password", password.ifBlank { oldCredentials.optString("password") }).toString()
        dao.saveProfile(current.copy(name = name.trim(), encryptedSource = if (source.isBlank()) current.encryptedSource else secrets.encrypt(source),
            encryptedCredentials = secrets.encrypt(nextCredentials), encryptedEpg = if (epg.isBlank()) current.encryptedEpg else secrets.encrypt(epg)))
        if ((type == ProfileType.MANUAL || type == ProfileType.LOCAL_MEDIA) && source.isNotBlank()) {
            replaceChannels(
                id,
                listOf(ParsedChannel(name.trim(), source, if (type == ProfileType.LOCAL_MEDIA) "Local video" else "Manual", null, null, null, emptyMap())),
            )
        }
    }

    suspend fun importDemoFixture(profileName: String = "Test M3U"): Long = withContext(Dispatchers.IO) {
        require(profileName.isNotBlank() && profileName.length <= 80) { "Enter a profile name" }
        val profileId = dao.saveProfile(ProfileEntity(name = profileName.trim(), type = ProfileType.MANUAL.name, encryptedSource = secrets.encrypt("fixture://demo.mp4")))
        val parsed = context.assets.open("demo.m3u").bufferedReader().use { M3uParser(allowBundledFixture = true).parse(it) }
        replaceChannels(profileId, parsed)
        profileId
    }

    /** Classifies channels imported before country/language support, without refetching playlists. */
    suspend fun backfillChannelOrigins() = withContext(Dispatchers.IO) {
        // Rules v2 read the broadcast feed ("@FR") first: re-file channels imported before that once.
        val prefs = context.getSharedPreferences("streamlab_catalog_migrations", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("origin_rules_v2", false)) {
            dao.channelsForAllProfiles().forEach { channel ->
                val feedCountry = ChannelClassifier.classify(tvgId = channel.tvgId).country
                if (channel.tvgId?.contains('@') == true && feedCountry != null && feedCountry != channel.country) {
                    dao.setOrigin(channel.id, feedCountry, channel.language)
                }
            }
            prefs.edit().putBoolean("origin_rules_v2", true).apply()
        }
        if (!prefs.getBoolean("playlist_country_v1", false)) {
            dao.profiles().first().filter { it.type == ProfileType.M3U_URL.name }.forEach { profile ->
                val listCountry = ChannelClassifier.countryFromPlaylistUrl(runCatching { secrets.decrypt(profile.encryptedSource) }.getOrNull())
                    ?: return@forEach
                dao.channelsForProfile(profile.id).forEach { channel ->
                    val origin = ChannelClassifier.classify(tvgId = channel.tvgId, group = channel.category, name = channel.name, playlistCountry = listCountry)
                    if (origin.country != null && origin.country != channel.country) dao.setOrigin(channel.id, origin.country, channel.language)
                }
            }
            prefs.edit().putBoolean("playlist_country_v1", true).apply()
        }
        // Names imported before the clean-up ("20 Minutes TV (1080p)") are tidied once.
        if (!prefs.getBoolean("clean_names_v1", false)) {
            dao.channelsForAllProfiles().forEach { channel ->
                val clean = app.cleanroom.streamlab.core.StationTidy.cleanChannelName(channel.name)
                if (clean != channel.name) dao.setName(channel.id, clean)
            }
            prefs.edit().putBoolean("clean_names_v1", true).apply()
        }
        dao.channelsWithoutOrigin().forEach { channel ->
            val origin = ChannelClassifier.classify(tvgId = channel.tvgId, group = channel.category, name = channel.name)
            if (origin.country != null || origin.language != null) dao.setOrigin(channel.id, origin.country, origin.language)
        }
    }

    /** Removes the bundled channel list shipped by pre-release builds; StreamLab no longer provides channels. */
    suspend fun removeRetiredBundledProfile() = withContext(Dispatchers.IO) {
        dao.profileByName(RETIRED_BUNDLED_PROFILE)?.let { deleteProfile(it.id) }
    }

    suspend fun addRadioStation(
        countryCode: String,
        name: String,
        url: String,
        favicon: String?,
        stationUuid: String,
        backups: List<String> = emptyList(),
    ): Long = withContext(Dispatchers.IO) {
        val code = countryCode.trim().uppercase().take(2)
        require(code.matches(Regex("[A-Z]{2}"))) { "Code pays invalide" }
        require(name.isNotBlank()) { "Nom de radio manquant" }
        val safeUrl = SourcePolicy.requireMediaRemote(url)
        val safeLogo = favicon?.let { runCatching { SourcePolicy.requireMediaRemote(it) }.getOrNull() }
        val profileName = "Radio · $code"
        val existing = dao.profileByName(profileName)
        val profileId = if (existing == null) {
            dao.saveProfile(ProfileEntity(
                name = profileName,
                type = ProfileType.MANUAL.name,
                encryptedSource = secrets.encrypt(safeUrl),
            ))
        } else {
            existing.id
        }
        if (dao.settings(profileId) == null) dao.saveSettings(ProfileSettingsEntity(profileId))
        val identity = sha256("radio:$stationUuid:$safeUrl")
        val previous = dao.channelsForProfile(profileId).firstOrNull { it.identity == identity }
        val entity = ChannelEntity(
            id = previous?.id ?: 0,
            profileId = profileId,
            identity = identity,
            name = name.trim().take(120),
            category = "Radio $code",
            tvgId = stationUuid,
            tvgName = name.trim().take(120),
            logo = safeLogo?.let(secrets::encrypt),
            encryptedUrl = secrets.encrypt(safeUrl),
            encryptedHeaders = secrets.encrypt("{}"),
            encryptedFallbackUrl = SourceFailover.encode(backups.mapNotNull { runCatching { SourcePolicy.requireMediaRemote(it) }.getOrNull() })
                .let { if (it.isEmpty()) "" else secrets.encrypt(it) },
        )
        dao.upsertChannels(listOf(entity))
        dao.channelsForProfile(profileId).first { it.identity == identity }.id
    }

    /** Lets this home server (and only it) use its local-network address. */
    private fun allowHomeServer(source: String) {
        SourcePolicy.origin(source)?.let { SourcePolicy.homeOrigins = SourcePolicy.homeOrigins + it }
    }

    /** At start-up: re-allow the home servers the user added earlier. */
    suspend fun loadHomeServers() = withContext(Dispatchers.IO) {
        dao.profiles().first().filter { it.type == ProfileType.WEBDAV.name }.forEach { profile ->
            runCatching { allowHomeServer(secrets.decrypt(profile.encryptedSource)) }
        }
    }

    data class ShareAccess(val url: String, val username: String, val password: String)

    suspend fun shareAccess(profileId: Long): ShareAccess = withContext(Dispatchers.IO) {
        val profile = dao.profile(profileId) ?: throw IllegalArgumentException("Profile missing")
        val credentials = JSONObject(secrets.decrypt(profile.encryptedCredentials).ifBlank { "{}" })
        ShareAccess(secrets.decrypt(profile.encryptedSource), credentials.optString("username"), credentials.optString("password"))
    }

    /** A film picked on the home server joins "Vidéos" (and "Continuer à regarder"). */
    suspend fun addShareVideo(profileId: Long, name: String, url: String, headers: Map<String, String>): Long = withContext(Dispatchers.IO) {
        val safe = SourcePolicy.requireMediaRemote(url)
        val identity = sha256("share:$safe")
        val previous = dao.channelsForProfile(profileId).firstOrNull { it.identity == identity }
        dao.upsertChannels(listOf(ChannelEntity(
            id = previous?.id ?: 0, profileId = profileId, identity = identity,
            name = name.substringBeforeLast('.').take(120), category = "Local video", tvgId = null, tvgName = null, logo = null,
            encryptedUrl = secrets.encrypt(safe), encryptedHeaders = secrets.encrypt(JSONObject(headers).toString()),
        )))
        dao.channelsForProfile(profileId).first { it.identity == identity }.id
    }

    suspend fun deleteProfile(id: Long) = withContext(Dispatchers.IO) {
        val removedChannelIds = dao.channelsForProfile(id).mapTo(HashSet()) { it.id }
        val persistedId = OpaqueMediaId.channelId(preferences?.lastPlaybackMediaId())
        dao.deleteProfile(id)
        if (persistedId != null && persistedId in removedChannelIds) {
            val replacementId = dao.latestPlayedChannelId()?.takeIf { dao.channel(it) != null }
            preferences?.setLastPlaybackMediaId(replacementId?.let(OpaqueMediaId::channel))
        }
    }

    suspend fun setRefreshHours(id: Long, epg: Boolean, hours: Int) = withContext(Dispatchers.IO) {
        require(hours == 0 || hours >= 15)
        val current = dao.settings(id) ?: ProfileSettingsEntity(id)
        dao.saveSettings(if (epg) current.copy(epgRefreshHours = hours) else current.copy(refreshHours = hours))
    }

    suspend fun updateProfile(id: Long, source: String, username: String = "", password: String = "", epg: String = "") = withContext(Dispatchers.IO) {
        val profile = dao.profile(id) ?: throw IllegalArgumentException("Profile missing")
        when (ProfileType.valueOf(profile.type)) {
            ProfileType.LOCAL_M3U, ProfileType.LOCAL_MEDIA -> SourcePolicy.requireLocalContent(source)
            ProfileType.M3U_URL, ProfileType.XTREAM, ProfileType.MANUAL -> SourcePolicy.requireMediaRemote(source)
            ProfileType.STALKER -> SourcePolicy.requireRemote(source)
            ProfileType.WEBDAV -> { allowHomeServer(source); SourcePolicy.requireMediaRemote(source) }
        }
        if (epg.isNotBlank()) SourcePolicy.requireRemote(epg)
        val credentials = if (username.isNotBlank() || password.isNotBlank())
            secrets.encrypt(JSONObject().put("username", username).put("password", password).toString())
        else profile.encryptedCredentials
        dao.saveProfile(profile.copy(encryptedSource = secrets.encrypt(source), encryptedCredentials = credentials,
            encryptedEpg = secrets.encrypt(epg)))
    }

    suspend fun refreshProfile(id: Long): Int = withContext(Dispatchers.IO) {
        val profile = dao.profile(id) ?: throw IllegalArgumentException("Profile missing")
        val type = ProfileType.valueOf(profile.type)
        val source = secrets.decrypt(profile.encryptedSource)
        val bytes = when (type) {
            ProfileType.M3U_URL -> network.fetchMedia(source, 8_000_000)
            ProfileType.LOCAL_M3U -> context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= 8_000_000) { "Playlist exceeds size limit" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: throw IllegalArgumentException("Cannot read selected document")
            ProfileType.MANUAL, ProfileType.LOCAL_MEDIA, ProfileType.WEBDAV -> return@withContext dao.channelsForProfile(id).size
            ProfileType.XTREAM -> {
                val credentials = JSONObject(secrets.decrypt(profile.encryptedCredentials))
                val username = URLEncoder.encode(credentials.getString("username"), "UTF-8")
                val password = URLEncoder.encode(credentials.getString("password"), "UTF-8")
                val base = SourcePolicy.requireMediaRemote(source).trimEnd('/')
                network.fetchMedia("$base/get.php?username=$username&password=$password&type=m3u_plus&output=ts", 8_000_000)
            }
            ProfileType.STALKER -> throw UnsupportedOperationException("Stalker protocol is not implemented")
        }
        val parsed = M3uParser().parse(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8))
        replaceChannels(id, parsed, playlistCountry = if (type == ProfileType.M3U_URL) ChannelClassifier.countryFromPlaylistUrl(source) else null)
        dao.markRefreshed(id, System.currentTimeMillis())
        parsed.size
    }

    private suspend fun replaceChannels(profileId: Long, parsed: List<ParsedChannel>, playlistCountry: String? = null) {
        val previous = dao.channelsForProfile(profileId).associateBy { it.identity }
        // Same channel listed several times (HD, backup…): each copy backs up the others.
        val backups = SourceFailover.backupsByIndex(parsed.map { SourceFailover.sameChannelKey(it.name, it.tvgId) to it.url })
        val next = parsed.mapIndexed { index, channel ->
            val identity = sha256(channel.url)
            val origin = ChannelClassifier.classify(channel.tvgCountry, channel.tvgLanguage, channel.tvgId, channel.group, channel.name, playlistCountry)
            ChannelEntity(
                id = previous[identity]?.id ?: 0,
                profileId = profileId,
                identity = identity,
                name = app.cleanroom.streamlab.core.StationTidy.cleanChannelName(channel.name),
                category = channel.group,
                tvgId = channel.tvgId,
                tvgName = channel.tvgName,
                logo = channel.logo?.let(secrets::encrypt),
                encryptedUrl = secrets.encrypt(channel.url),
                encryptedHeaders = secrets.encrypt(JSONObject(channel.headers).toString()),
                encryptedFallbackUrl = SourceFailover.encode(backups[index]).let { if (it.isEmpty()) "" else secrets.encrypt(it) },
                country = origin.country,
                language = origin.language,
            )
        }
        database.withTransaction {
            // Keep existing row ids where possible so favorite and recent state survives refresh.
            val nextIds = next.mapTo(HashSet()) { it.identity }
            previous.values.filter { it.identity !in nextIds }.map { it.id }.chunked(500).forEach { dao.deleteChannelsByIds(it) }
            next.chunked(500).forEach { dao.upsertChannels(it) }
        }
    }

    suspend fun refreshGuide(id: Long): Int = withContext(Dispatchers.IO) {
        val profile = dao.profile(id) ?: throw IllegalArgumentException("Profile missing")
        val epg = secrets.decrypt(profile.encryptedEpg)
        require(epg.isNotBlank()) { "Add an EPG URL to this profile" }
        val payload = network.fetch(epg, 16_000_000)
        val guide = GuidePayload.open(payload, epg).use { XmlTvParser().parse(it) }
        val channels = dao.channelsForProfile(id)
        val byId = channels.filter { !it.tvgId.isNullOrBlank() }.associateBy { it.tvgId!!.lowercase() }
        val byName = channels.flatMap { channel ->
            listOf(channel.name.trim().lowercase() to channel) +
                listOfNotNull(channel.tvgName?.takeIf { it.isNotBlank() }?.trim()?.lowercase()?.let { it to channel })
        }.toMap()
        val mapped = guide.programmes.mapNotNull { programme ->
            val channel = byId[programme.channel.lowercase()] ?: byName[guide.channelNames[programme.channel]?.lowercase()] ?: byName[programme.channel.lowercase()]
            channel?.let { ProgrammeEntity(channelId = it.id, title = programme.title, startMs = programme.start, endMs = programme.stop) }
        }
        database.withTransaction { dao.deleteProgrammes(id); dao.insertProgrammes(mapped) }
        mapped.size
    }

    suspend fun setFallback(channelId: Long, url: String) = withContext(Dispatchers.IO) {
        val safe = if (url.isBlank()) "" else SourcePolicy.requireMediaRemote(url)
        dao.setFallback(channelId, secrets.encrypt(safe))
    }

    suspend fun toggleFavorite(channelId: Long) = withContext(Dispatchers.IO) {
        if (dao.favorite(channelId) == null) dao.addFavorite(FavoriteEntity(channelId)) else dao.removeFavorite(channelId)
    }
    suspend fun markPlayed(channelId: Long) = withContext(Dispatchers.IO) {
        require(dao.channel(channelId) != null) { "Channel missing" }
        val previous = dao.recent(channelId)
        dao.markRecent(RecentEntity(
            channelId = channelId,
            playedAt = System.currentTimeMillis(),
            positionMs = previous?.positionMs ?: 0L,
            durationMs = previous?.durationMs ?: 0L,
        ))
        preferences?.setLastPlaybackMediaId(OpaqueMediaId.channel(channelId))
    }

    suspend fun updatePlaybackProgress(channelId: Long, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        if (dao.channel(channelId) == null) return@withContext
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, safeDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        if (dao.recent(channelId) == null) {
            dao.markRecent(RecentEntity(channelId, System.currentTimeMillis(), safePosition, safeDuration))
        } else {
            dao.updateRecentProgress(channelId, safePosition, safeDuration)
        }
    }

    suspend fun resumePositionMs(channelId: Long): Long = withContext(Dispatchers.IO) {
        val recent = dao.recent(channelId) ?: return@withContext 0L
        val position = recent.positionMs
        val duration = recent.durationMs
        if (position < 10_000L || duration <= 0L) return@withContext 0L
        if (duration - position <= 30_000L || position >= (duration * 95L / 100L)) return@withContext 0L
        position
    }

    suspend fun resumeMediaId(): String? = withContext(Dispatchers.IO) {
        val persisted = preferences?.lastPlaybackMediaId()
        val persistedId = OpaqueMediaId.channelId(persisted)
        if (persistedId != null && dao.channel(persistedId) != null) return@withContext OpaqueMediaId.channel(persistedId)

        // Migration/fallback for installs that only have the historical Recent row.
        val recentId = dao.latestPlayedChannelId()?.takeIf { dao.channel(it) != null }
        if (recentId == null) {
            if (!persisted.isNullOrBlank()) preferences?.setLastPlaybackMediaId(null)
            return@withContext null
        }
        val mediaId = OpaqueMediaId.channel(recentId)
        preferences?.setLastPlaybackMediaId(mediaId)
        mediaId
    }
    /** What is on now on a channel and what follows, from its programme guide (empty without a guide). */
    suspend fun nowNext(channelId: Long, now: Long = System.currentTimeMillis()): app.cleanroom.streamlab.core.NowNext.Result = withContext(Dispatchers.IO) {
        app.cleanroom.streamlab.core.NowNext.pick(dao.upcoming(channelId, now).map { app.cleanroom.streamlab.core.NowNext.Slot(it.title, it.startMs, it.endMs) }, now)
    }

    /** Public logo address of a channel or station, if it has one. */
    suspend fun logoUrl(channelId: Long): String? = withContext(Dispatchers.IO) {
        dao.channel(channelId)?.logo?.let { runCatching { secrets.decrypt(it) }.getOrNull() }?.takeIf { it.isNotBlank() }
    }

    suspend fun playbackSource(channelId: Long): PlaybackSource = withContext(Dispatchers.IO) {
        val channel = dao.channel(channelId) ?: throw IllegalArgumentException("Channel missing")
        val headersJson = JSONObject(secrets.decrypt(channel.encryptedHeaders).ifBlank { "{}" })
        val headers = headersJson.keys().asSequence().associateWith { headersJson.getString(it) }
        val raw = secrets.decrypt(channel.encryptedUrl)
        val safe = when {
            raw == "fixture://demo.mp4" -> raw
            raw.startsWith("content://", ignoreCase = true) -> SourcePolicy.requireLocalContent(raw)
            else -> SourcePolicy.requireMediaRemote(raw)
        }
        val backups = SourceFailover.decode(secrets.decrypt(channel.encryptedFallbackUrl))
            .mapNotNull { runCatching { SourcePolicy.requireMediaRemote(it) }.getOrNull() }
        PlaybackSource(channel.name, safe, headers, backups.firstOrNull(), backups)
    }

    fun exportPortable(profiles: List<ProfileEntity>): String = JSONObject()
        .put("format", "streamlab-portable-v1")
        .put("profiles", JSONArray().apply { profiles.forEach { put(JSONObject().put("name", it.name).put("type", it.type)) } })
        .toString(2)

    suspend fun importPortable(json: String): Int = withContext(Dispatchers.IO) {
        require(json.length <= 1_000_000) { "Import too large" }
        val root = JSONObject(json)
        require(root.getString("format") == "streamlab-portable-v1") { "Unsupported format" }
        val items = root.getJSONArray("profiles")
        require(items.length() <= 100) { "Too many profiles" }
        val profilesToImport = List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val name = item.getString("name").take(80)
            val type = ProfileType.valueOf(item.getString("type"))
            require(type != ProfileType.STALKER) { "Stalker sources are not supported; import an M3U or Xtream source instead" }
            ProfileEntity(name = name, type = type.name)
        }
        profilesToImport.forEach { dao.saveProfile(it) }
        profilesToImport.size
    }

    /** Favourite radios and public M3U links, ready for a QR share code (never credentials). */
    suspend fun shareBundle(): ShareCode.Bundle = withContext(Dispatchers.IO) {
        val profiles = dao.profiles().first()
        val radioProfiles = profiles.filter { it.name.startsWith("Radio · ") }.associateBy { it.id }
        val favourites = dao.channels().first().filter { it.favorite && it.profileId in radioProfiles }
        val radios = favourites.mapNotNull { row ->
            val entity = dao.channel(row.id) ?: return@mapNotNull null
            val country = radioProfiles[row.profileId]?.name?.removePrefix("Radio · ")?.trim()
            ShareCode.Radio(row.name, secrets.decrypt(entity.encryptedUrl), country)
        }
        val playlists = profiles.filter { it.type == ProfileType.M3U_URL.name }.map {
            ShareCode.Playlist(it.name, secrets.decrypt(it.encryptedSource))
        }
        ShareCode.Bundle(radios, playlists)
    }

    /** Adds a friend's shared radios (as favourites) and playlists. Returns how many items were added. */
    suspend fun importShare(bundle: ShareCode.Bundle): Int = withContext(Dispatchers.IO) {
        var added = 0
        bundle.radios.forEach { radio ->
            val id = addRadioStation(radio.country ?: "ZZ", radio.name, radio.url, null, "shared-" + sha256(radio.url).take(32))
            if (dao.favorite(id) == null) dao.addFavorite(FavoriteEntity(id))
            added++
        }
        val existing = dao.profiles().first().map { it.name }.toSet()
        bundle.playlists.forEach { playlist ->
            val name = if (playlist.name in existing) "${playlist.name} (partage)".take(80) else playlist.name
            val id = createProfile(name, ProfileType.M3U_URL, playlist.url)
            runCatching { refreshProfile(id) }
            added++
        }
        added
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class PlaybackSource(val name: String, val url: String, val headers: Map<String, String>, val fallbackUrl: String?,
    val backups: List<String> = listOfNotNull(fallbackUrl)) {
    /** Main address then backups, at most three: what the player tries, in order, when one fails. */
    val allUrls: List<String> get() = SourceFailover.ordered(url, backups)
}
