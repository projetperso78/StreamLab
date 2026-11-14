package app.cleanroom.streamlab

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.player.AutoLibrary
import app.cleanroom.streamlab.player.PlaybackService
import app.cleanroom.streamlab.player.AutoMediaSourceFactory
import app.cleanroom.streamlab.services.RadioCountry
import app.cleanroom.streamlab.services.RadioDirectoryGateway
import app.cleanroom.streamlab.services.RadioStation
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.data.ProfileEntity
import app.cleanroom.streamlab.data.ChannelEntity
import app.cleanroom.streamlab.data.ProfileSettingsEntity
import app.cleanroom.streamlab.data.ProfileType
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class AndroidAutoTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val client get() = instrumentation.context

    @Test fun exportedMediaLibraryHasAndroidAutoDeclaration() {
        val info = target.packageManager.getApplicationInfo(target.packageName, PackageManager.GET_META_DATA)
        val resource = info.metaData.getInt("com.google.android.gms.car.application")
        assertTrue(resource != 0)
        val parser = target.resources.getXml(resource)
        val xml = buildString {
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "uses")
                    append(parser.getAttributeValue(null, "name"))
                parser.next()
            }
        }
        parser.close()
        assertEquals("media", xml)
        val service = target.packageManager.queryIntentServices(
            Intent("android.media.browse.MediaBrowserService").setPackage(target.packageName), 0)
        assertTrue(service.any { it.serviceInfo.name == PlaybackService::class.java.name && it.serviceInfo.exported })
    }

    @Test fun browserCanBrowseOpaqueMetadata() {
        val app = target.applicationContext as StreamLabApplication
        runBlocking { app.repository.importDemoFixture() }
        val browser = browser(target)
        try {
            val command = SessionCommand("app.cleanroom.streamlab.PLAY_SOURCE", Bundle.EMPTY)
            instrumentation.runOnMainSync { assertTrue(browser.availableSessionCommands.contains(command)) }
            val root = onMain { browser.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS).value!!
            assertTrue(root.mediaMetadata.isBrowsable == true)
            val branches = onMain { browser.getChildren(root.mediaId, 0, 100, null) }
                .get(10, TimeUnit.SECONDS).value!!
            assertEquals(listOf("radio", "tv", "favorites", "recent", "profiles"), branches.map { it.mediaId })
            val profiles = onMain { browser.getChildren("profiles", 0, 100, null) }
                .get(10, TimeUnit.SECONDS).value!!
            val profile = profiles.first { it.mediaMetadata.title == "Test M3U" }
            val group = onMain { browser.getChildren(profile.mediaId, 0, 100, null) }
                .get(10, TimeUnit.SECONDS).value!!.first()
            val channel = onMain { browser.getChildren(group.mediaId, 0, 100, null) }
                .get(10, TimeUnit.SECONDS).value!!.first()
            assertTrue(channel.mediaMetadata.isPlayable == true)
            assertFalse(channel.mediaMetadata.isBrowsable == true)
            assertNull(channel.localConfiguration)
            assertFalse(channel.toString().contains("fixture://"))
        } finally { instrumentation.runOnMainSync { browser.release() } }
    }

    @Test fun demoM3uImportsBundledFixtureWithoutNetwork() {
        val app = target.applicationContext as StreamLabApplication
        val profileId = runBlocking { app.repository.importDemoFixture("Offline demo verification") }
        val browser = browser(target)
        val ready = CountDownLatch(1)
        val playbackError = AtomicReference<PlaybackException?>()
        val videoTrackSeen = AtomicBoolean(false)
        try {
            val channelId = runBlocking { app.repository.dao.channelsForProfile(profileId).single().id }
            val source = runBlocking { app.repository.playbackSource(channelId) }
            assertEquals("Demo must resolve to the bundled offline pattern", "fixture://demo.mp4", source.url)
            val profile = requireNotNull(runBlocking { app.repository.dao.profile(profileId) })
            assertEquals("Demo profile must not retain a remote address", "fixture://demo.mp4", app.secrets.decrypt(profile.encryptedSource))
            instrumentation.runOnMainSync {
                browser.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError.set(error)
                        ready.countDown()
                    }
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        if (tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }) videoTrackSeen.set(true)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            if (browser.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }) videoTrackSeen.set(true)
                            ready.countDown()
                        }
                    }
                })
            }
            val extras = Bundle().apply {
                putString("url", source.url)
                putString("title", source.name)
                putString("media_id", OpaqueMediaId.channel(channelId))
                putBundle("headers", Bundle.EMPTY)
            }
            val result = onMain {
                browser.sendCustomCommand(SessionCommand(PlaybackService.PLAY_SOURCE, Bundle.EMPTY), extras)
            }.get(10, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            assertTrue("Bundled demo pattern never reached READY", ready.await(20, TimeUnit.SECONDS))
            assertNull(playbackError.get())
            assertTrue("Bundled demo pattern has no decoded video track", videoTrackSeen.get())
            instrumentation.runOnMainSync {
                assertEquals(OpaqueMediaId.channel(channelId), browser.currentMediaItem?.mediaId)
                assertFalse(browser.currentMediaItem.toString().contains("fixture://"))
            }
        } finally {
            instrumentation.runOnMainSync { browser.release() }
            runBlocking { app.repository.deleteProfile(profileId) }
        }
    }

    @Test fun carCatalogIncludesRadioCountryThenAddedStationForClioValidation() {
        val app = target.applicationContext as StreamLabApplication
        runBlocking {
            app.repository.addRadioStation(
                countryCode = "FR",
                name = "Radio Clio QA",
                url = "https://example.org/radio.mp3",
                favicon = "https://example.org/radio.png",
                stationUuid = "clio-radio-qa",
            )
        }
        val browser = browser(target)
        try {
            val countries = onMain { browser.getChildren("radio", 0, 300, null) }
                .get(15, TimeUnit.SECONDS).value!!
            val france = countries.firstOrNull { it.mediaId == AutoLibrary.radioCountryId("FR") }
            assertNotNull("France country node must be exposed before radio stations", france)
            assertTrue(france!!.mediaMetadata.isBrowsable == true)
            assertFalse(france.mediaMetadata.isPlayable == true)

            val radios = onMain { browser.getChildren(france.mediaId, 0, 300, null) }
                .get(15, TimeUnit.SECONDS).value!!
            val radio = radios.firstOrNull { it.mediaMetadata.title == "Radio Clio QA" }
            assertNotNull("Added radio must be exposed under France for Clio validation", radio)
            assertTrue(radio!!.mediaMetadata.isPlayable == true)
            assertNull(radio.localConfiguration)
            assertFalse(radio.toString().contains("example.org/radio.mp3"))
        } finally {
            instrumentation.runOnMainSync { browser.release() }
            runBlocking { app.repository.dao.profileByName("Radio · FR")?.id?.let { app.repository.deleteProfile(it) } }
        }
    }

    @Test fun steeringWheelNextAndPreviousZapThroughTheChosenCountry() {
        val app = target.applicationContext as StreamLabApplication
        runBlocking {
            app.repository.addRadioStation("FR", "Radio Zap A", "https://example.org/zap-a.mp3", null, "zap-a")
            app.repository.addRadioStation("FR", "Radio Zap B", "https://example.org/zap-b.mp3", null, "zap-b")
        }
        val browser = browser(target)
        try {
            val radios = onMain { browser.getChildren(AutoLibrary.radioCountryId("FR"), 0, 300, null) }
                .get(15, TimeUnit.SECONDS).value!!
            val first = radios.first { it.mediaMetadata.title == "Radio Zap A" }
            instrumentation.runOnMainSync { browser.setMediaItem(first); browser.prepare() }
            waitForTitle(browser, "Radio Zap A")
            instrumentation.runOnMainSync {
                assertTrue(browser.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
                assertTrue(browser.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
                browser.seekToNext()
            }
            waitForTitle(browser, "Radio Zap B")
            instrumentation.runOnMainSync { browser.seekToPrevious() }
            waitForTitle(browser, "Radio Zap A")
        } finally {
            instrumentation.runOnMainSync { browser.stop(); browser.release() }
            runBlocking { app.repository.dao.profileByName("Radio · FR")?.id?.let { app.repository.deleteProfile(it) } }
        }
    }

    private fun waitForTitle(browser: MediaBrowser, title: String) {
        val deadline = System.currentTimeMillis() + 15_000
        var seen: String? = null
        while (System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync { seen = browser.currentMediaItem?.mediaMetadata?.title?.toString() }
            if (seen == title) return
            Thread.sleep(200)
        }
        fail("Expected '$title' to be playing, saw '$seen'")
    }

    @Test fun sharedPlayerKeepsVideoAvailableForPassengerSurfaceAndTimelineOpaque() {
        val app = target.applicationContext as StreamLabApplication
        runBlocking { app.repository.importDemoFixture() }
        val browser = browser(target)
        try {
            val profiles = onMain { browser.getChildren("profiles", 0, 100, null) }
                .get(10, TimeUnit.SECONDS).value!!
            val profile = profiles.first { it.mediaMetadata.title == "Test M3U" }
            val group = onMain { browser.getChildren(profile.mediaId, 0, 100, null) }
                .get(10, TimeUnit.SECONDS).value!!.first()
            val channel = onMain { browser.getChildren(group.mediaId, 0, 100, null) }
                .get(10, TimeUnit.SECONDS).value!!.first()
            instrumentation.runOnMainSync { browser.setMediaItem(channel) }
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                var loaded = false
                instrumentation.runOnMainSync { loaded = browser.mediaItemCount > 0 &&
                    !browser.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO) }
                if (loaded) break
                Thread.sleep(100)
            }
            instrumentation.runOnMainSync {
                assertTrue(browser.mediaItemCount > 0)
                assertFalse(browser.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO))
                assertFalse(browser.currentMediaItem.toString().contains("fixture://"))
                assertFalse(browser.currentMediaItem.toString().contains("asset:///"))
            }
        } finally { instrumentation.runOnMainSync { browser.release() } }
    }

    @Test fun searchPlaybackResolvesOnlyCatalogItems() {
        val app = target.applicationContext as StreamLabApplication
        val profileName = "Search fixture ${System.nanoTime()}"
        val profileId = runBlocking { app.repository.importDemoFixture(profileName) }
        val channelName = runBlocking { app.repository.dao.channelsForProfile(profileId).single().name }
        val browser = browser(target)
        try {
            val request = MediaItem.Builder()
                .setRequestMetadata(MediaItem.RequestMetadata.Builder()
                    .setSearchQuery(channelName).build())
                .build()
            instrumentation.runOnMainSync { browser.setMediaItem(request) }
            val deadline = System.currentTimeMillis() + 10_000
            var resolved = false
            while (System.currentTimeMillis() < deadline && !resolved) {
                instrumentation.runOnMainSync {
                    resolved = browser.currentMediaItem?.mediaId?.startsWith("channel:") == true
                }
                if (!resolved) android.os.SystemClock.sleep(100)
            }
            assertTrue("Search request did not resolve a catalog channel", resolved)
            instrumentation.runOnMainSync {
                assertFalse(browser.currentMediaItem.toString().contains("fixture://"))
            }
        } finally {
            instrumentation.runOnMainSync { browser.release() }
            runBlocking { app.repository.deleteProfile(profileId) }
        }
    }

    @Test fun coldServicePlayResumesMostRecentChannel() {
        val app = target.applicationContext as StreamLabApplication
        val profileId = runBlocking { app.repository.importDemoFixture() }
        val channelId = runBlocking { app.repository.dao.channelsForProfile(profileId).single().id }
        runBlocking { app.repository.markPlayed(channelId) }

        target.stopService(Intent(target, PlaybackService::class.java))
        android.os.SystemClock.sleep(500)

        val browser = browser(target)
        try {
            instrumentation.runOnMainSync {
                assertEquals(0, browser.mediaItemCount)
                browser.play()
            }
            val deadline = System.currentTimeMillis() + 10_000
            var resumed = false
            var selectedMostRecentChannel = false
            val focusIsUnqualifiedOnThisHarness = isAaos35FocusRestricted()
            var observedPlaybackState = -1
            var observedPlayWhenReady = false
            while (System.currentTimeMillis() < deadline && !resumed) {
                instrumentation.runOnMainSync {
                    observedPlaybackState = browser.playbackState
                    observedPlayWhenReady = browser.playWhenReady
                    selectedMostRecentChannel = browser.mediaItemCount == 1 &&
                        browser.currentMediaItem?.mediaId == "channel:$channelId" &&
                        !browser.currentMediaItem.toString().contains("fixture://") &&
                        !browser.currentMediaItem.toString().contains("asset:///")
                    resumed = selectedMostRecentChannel &&
                        (focusIsUnqualifiedOnThisHarness || browser.isPlaying)
                }
                if (!resumed) android.os.SystemClock.sleep(100)
            }
            assertTrue(
                "Cold playback resumption did not restore the most recent channel; " +
                    "selected=$selectedMostRecentChannel, state=$observedPlaybackState, " +
                    "playWhenReady=$observedPlayWhenReady",
                resumed,
            )
            if (focusIsUnqualifiedOnThisHarness) {
                android.util.Log.i(
                    "StreamLabFocusProbe",
                    "AAOS 35 instrumentation verified resume selection only; " +
                        "audio focus/playback is unqualified without a foreground car media host " +
                        "(state=$observedPlaybackState, playWhenReady=$observedPlayWhenReady)",
                )
            }
            instrumentation.runOnMainSync {
                assertEquals("channel:$channelId", browser.currentMediaItem?.mediaId)
            }
        } finally {
            instrumentation.runOnMainSync { browser.release() }
            runBlocking { app.repository.dao.deleteProfile(profileId) }
        }
    }

    @Test fun foregroundPassengerActivityRecoversAudioFocusInterruption() {
        val app = target.applicationContext as StreamLabApplication
        val fixture = createOfflineAudioProfile(app, "Foreground audio focus")
        val channelId = runBlocking { app.repository.dao.channelsForProfile(fixture.profileId).single().id }
        runBlocking { app.repository.markPlayed(channelId) }
        target.stopService(Intent(target, PlaybackService::class.java))

        var activity: CarServicesActivity? = null
        var browser: MediaBrowser? = null
        var audioManager: AudioManager? = null
        var competingFocusRequest: AudioFocusRequest? = null
        try {
            val foregroundActivity = instrumentation.startActivitySync(
                Intent(target, CarServicesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as CarServicesActivity
            activity = foregroundActivity
            assertTrue(
                "Passenger activity must be foreground for this focus probe",
                foregroundActivity.window.decorView.hasWindowFocus(),
            )

            val player = browser(target)
            browser = player
            instrumentation.runOnMainSync {
                assertEquals(0, player.mediaItemCount)
                player.play()
            }

            val deadline = System.currentTimeMillis() + 10_000
            var selected = false
            var isPlaying = false
            var state = Player.STATE_IDLE
            var playWhenReady = false
            while (System.currentTimeMillis() < deadline && !isPlaying) {
                instrumentation.runOnMainSync {
                    state = player.playbackState
                    playWhenReady = player.playWhenReady
                    selected = player.mediaItemCount == 1 &&
                        player.currentMediaItem?.mediaId == "channel:$channelId"
                    isPlaying = selected && player.isPlaying
                }
                if (!isPlaying) android.os.SystemClock.sleep(100)
            }
            assertTrue(
                "Foreground playback did not acquire audio focus; selected=$selected, " +
                    "state=$state, playWhenReady=$playWhenReady",
                isPlaying,
            )

            audioManager = target.getSystemService(AudioManager::class.java)
            val interruptionManager = requireNotNull(audioManager)
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener({}, Handler(Looper.getMainLooper()))
                .build()
            competingFocusRequest = focusRequest
            assertEquals(
                "A foreground competing audio client should receive transient focus",
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                interruptionManager.requestAudioFocus(focusRequest),
            )

            val pauseDeadline = System.currentTimeMillis() + 5_000
            var stillPlaying = true
            while (System.currentTimeMillis() < pauseDeadline && stillPlaying) {
                instrumentation.runOnMainSync { stillPlaying = player.isPlaying }
                if (stillPlaying) android.os.SystemClock.sleep(50)
            }
            assertTrue("Playback did not pause on transient audio-focus loss", !stillPlaying)

            assertEquals(
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                interruptionManager.abandonAudioFocusRequest(focusRequest),
            )
            competingFocusRequest = null

            val resumeDeadline = System.currentTimeMillis() + 5_000
            var resumedPlayback = false
            while (System.currentTimeMillis() < resumeDeadline && !resumedPlayback) {
                instrumentation.runOnMainSync { resumedPlayback = player.isPlaying }
                if (!resumedPlayback) android.os.SystemClock.sleep(50)
            }
            assertTrue(
                "Playback did not resume when transient audio focus returned",
                resumedPlayback,
            )
            android.util.Log.i(
                "StreamLabFocusProbe",
                "Foreground audio focus loss/pause/gain/resume verified; channel=$channelId, state=$state",
            )
        } finally {
            competingFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            instrumentation.runOnMainSync {
                browser?.release()
                activity?.finish()
            }
            target.stopService(Intent(target, PlaybackService::class.java))
            deleteOfflineAudioProfile(app, fixture)
        }
    }

    @Test fun deletingActivePhoneChannelRepairsResumeAndClearsSession() {
        val app = target.applicationContext as StreamLabApplication
        val fallbackFixture = createOfflineAudioProfile(app, "AAOS resume fallback")
        val fallbackProfile = fallbackFixture.profileId
        val fallbackChannel = runBlocking { app.repository.dao.channelsForProfile(fallbackProfile).single().id }
        val activeFixture = createOfflineAudioProfile(app, "AAOS resume active")
        val activeProfile = activeFixture.profileId
        val activeChannel = runBlocking { app.repository.dao.channelsForProfile(activeProfile).single().id }
        runBlocking {
            app.repository.markPlayed(fallbackChannel)
            app.repository.markPlayed(activeChannel)
        }
        val browser = browser(target)
        try {
            val source = runBlocking { app.repository.playbackSource(activeChannel) }
            val extras = Bundle().apply {
                putString("url", source.url)
                putString("title", source.name)
                putString("media_id", OpaqueMediaId.channel(activeChannel))
            }
            val command = SessionCommand("app.cleanroom.streamlab.PLAY_SOURCE", Bundle.EMPTY)
            val result = onMain { browser.sendCustomCommand(command, extras) }.get(10, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            val loadedDeadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < loadedDeadline) {
                var loaded = false
                instrumentation.runOnMainSync { loaded = browser.mediaItemCount == 1 }
                if (loaded) break
                android.os.SystemClock.sleep(100)
            }
            instrumentation.runOnMainSync {
                assertEquals(1, browser.mediaItemCount)
                assertEquals(OpaqueMediaId.channel(activeChannel), browser.currentMediaItem?.mediaId)
            }

            runBlocking { app.repository.deleteProfile(activeProfile) }

            val clearedDeadline = System.currentTimeMillis() + 10_000
            var cleared = false
            while (System.currentTimeMillis() < clearedDeadline && !cleared) {
                instrumentation.runOnMainSync { cleared = browser.mediaItemCount == 0 }
                if (!cleared) android.os.SystemClock.sleep(100)
            }
            assertTrue("Deleted active media item remained queued in MediaSession", cleared)
            assertEquals(OpaqueMediaId.channel(fallbackChannel), runBlocking { app.preferences.lastPlaybackMediaId() })

            val latch = CountDownLatch(1)
            val resultCode = AtomicInteger(Int.MIN_VALUE)
            val resultData = AtomicReference<String?>()
            val intent = Intent("app.cleanroom.streamlab.test.PROBE_RESUME")
                .setComponent(ComponentName(client.packageName, ExternalResumeProbe::class.java.name))
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    resultCode.set(getResultCode())
                    resultData.set(getResultData())
                    latch.countDown()
                }
            }
            client.sendOrderedBroadcast(intent, null, receiver, Handler(Looper.getMainLooper()), 99, null, null)
            assertTrue("External resume probe did not finish", latch.await(20, TimeUnit.SECONDS))
            assertEquals("External resume after deletion failed: ${resultData.get()}", 0, resultCode.get())
            assertTrue(
                "External resume did not move to the remaining valid recent channel: ${resultData.get()}",
                resultData.get()?.contains("resumed=${OpaqueMediaId.channel(fallbackChannel)}") == true,
            )
            assertEquals(
                "AAOS 35 receiver probes report the session selection separately from system focus policy",
                !isAaos35FocusRestricted(),
                resultData.get()?.contains("audioFocusVerified=true") == true,
            )
        } finally {
            instrumentation.runOnMainSync { browser.release() }
            runBlocking {
                app.preferences.setLastPlaybackMediaId(null)
            }
            deleteOfflineAudioProfile(app, activeFixture)
            deleteOfflineAudioProfile(app, fallbackFixture)
        }
    }

    @Test fun externalStaleCachedIdFallsBackToCurrentResume() {
        val app = target.applicationContext as StreamLabApplication
        val fixture = createOfflineAudioProfile(app, "AAOS stale resume")
        val profileId = fixture.profileId
        val channelId = runBlocking { app.repository.dao.channelsForProfile(profileId).single().id }
        runBlocking { app.repository.markPlayed(channelId) }
        target.stopService(Intent(target, PlaybackService::class.java))
        android.os.SystemClock.sleep(500)

        val latch = CountDownLatch(1)
        val resultCode = AtomicInteger(Int.MIN_VALUE)
        val resultData = AtomicReference<String?>()
        val staleId = OpaqueMediaId.channel(Long.MAX_VALUE)
        val intent = Intent("app.cleanroom.streamlab.test.PROBE_STALE_RESUME")
            .setComponent(ComponentName(client.packageName, ExternalStaleResumeProbe::class.java.name))
            .putExtra("stale_media_id", staleId)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                resultCode.set(getResultCode())
                resultData.set(getResultData())
                latch.countDown()
            }
        }
        try {
            client.sendOrderedBroadcast(intent, null, receiver, Handler(Looper.getMainLooper()), 99, null, null)
            assertTrue("External stale-ID probe did not finish", latch.await(20, TimeUnit.SECONDS))
            assertEquals("External stale-ID replay did not recover", 0, resultCode.get())
            assertTrue(
                "Unexpected stale-ID resume result: ${resultData.get()}",
                resultData.get()?.contains("resumed=${OpaqueMediaId.channel(channelId)}") == true,
            )
            assertEquals(
                "AAOS 35 receiver probes report the session selection separately from system focus policy",
                !isAaos35FocusRestricted(),
                resultData.get()?.contains("audioFocusVerified=true") == true,
            )
        } finally {
            target.stopService(Intent(target, PlaybackService::class.java))
            runBlocking {
                app.preferences.setLastPlaybackMediaId(null)
            }
            deleteOfflineAudioProfile(app, fixture)
        }
    }

    @Test fun resumeStateRejectsForgedAddressAndFallsBackToRoomRecent() = runBlocking {
        val app = target.applicationContext as StreamLabApplication
        val profileId = app.repository.importDemoFixture()
        val channelId = app.repository.dao.channelsForProfile(profileId).single().id
        try {
            app.repository.markPlayed(channelId)
            app.preferences.setLastPlaybackMediaId("https://example.com/private-stream")
            val resumed = app.repository.resumeMediaId()
            assertEquals(OpaqueMediaId.channel(channelId), resumed)
            assertEquals(OpaqueMediaId.channel(channelId), app.preferences.lastPlaybackMediaId())
        } finally {
            app.preferences.setLastPlaybackMediaId(null)
            app.repository.deleteProfile(profileId)
        }
    }

    @Test fun evictedAutoSourceReconstructsFromRoomUsingOpaqueId() = runBlocking {
        val app = target.applicationContext as StreamLabApplication
        val profileId = app.repository.importDemoFixture()
        val channelId = app.repository.dao.channelsForProfile(profileId).single().id
        try {
            val mediaId = OpaqueMediaId.channel(channelId)
            val factory = AutoMediaSourceFactory(target) { id -> app.repository.playbackSource(id) }
            val source = app.repository.playbackSource(channelId)
            factory.put(mediaId, source)
            repeat(200) { index -> factory.put("evict:$index", source) }

            val publicItem = MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri("streamlab://$mediaId")
                .build()
            val rebuilt = factory.createMediaSource(publicItem)
            assertEquals(mediaId, rebuilt.mediaItem.mediaId)
            assertFalse(rebuilt.mediaItem.toString().contains("fixture://"))
            assertFalse(rebuilt.mediaItem.toString().contains("asset:///"))
        } finally {
            app.repository.deleteProfile(profileId)
        }
    }
    @Test fun missingSourceDoesNotQueuePrivateOrUnplayableItem() {
        val app = target.applicationContext as StreamLabApplication
        val dao = app.repository.dao
        val profileId = runBlocking {
            val id = dao.saveProfile(ProfileEntity(name = "Missing source", type = "MANUAL"))
            dao.upsertChannels(listOf(ChannelEntity(
                profileId = id, identity = "missing-source", name = "No stream", category = "Test",
                tvgId = null, tvgName = null, logo = null, encryptedUrl = ""
            )))
            id
        }
        val channelId = runBlocking { dao.channelsForProfile(profileId).single().id }
        target.stopService(Intent(target, PlaybackService::class.java))
        android.os.SystemClock.sleep(500)
        val browser = browser(target)
        try {
            instrumentation.runOnMainSync { browser.clearMediaItems() }
            val clearDeadline = System.currentTimeMillis() + 10_000
            var cleared = false
            while (System.currentTimeMillis() < clearDeadline && !cleared) {
                instrumentation.runOnMainSync { cleared = browser.mediaItemCount == 0 }
                if (!cleared) android.os.SystemClock.sleep(50)
            }
            assertTrue("Existing session queue did not clear before invalid-source check", cleared)

            instrumentation.runOnMainSync {
                browser.setMediaItem(MediaItem.Builder().setMediaId("channel:$channelId")
                    .setUri("https://example.com/forged.mp4").build())
            }
            android.os.SystemClock.sleep(300)
            instrumentation.runOnMainSync {
                val current = browser.currentMediaItem
                assertNotEquals("Invalid source was queued", "channel:$channelId", current?.mediaId)
                assertFalse("Forged URI leaked into the session", current?.toString()?.contains("https://example.com/forged.mp4") == true)
            }
        } finally {
            instrumentation.runOnMainSync { browser.release() }
            runBlocking { dao.deleteProfile(profileId) }
        }
    }

    @Test fun countrySearchPrioritizesRadioCountryAndStations() {
        val app = target.applicationContext as StreamLabApplication
        val previousDirectory = app.radioDirectoryOverride
        app.radioDirectoryOverride = object : RadioDirectoryGateway {
            override suspend fun countries() = listOf(RadioCountry("FR", 1))

            override suspend fun stations(countryCode: String, query: String, limit: Int) =
                if (countryCode.equals("FR", ignoreCase = true)) {
                    listOf(RadioStation(
                        uuid = "streamlab-qa-fr",
                        name = "Radio France test",
                        countryCode = "FR",
                        streamUrl = "https://radio.example/live.mp3",
                        favicon = null,
                        codec = "MP3",
                        bitrate = 128,
                        votes = 1,
                        tags = "test",
                    )).take(limit)
                } else emptyList()
        }
        target.stopService(Intent(target, PlaybackService::class.java))
        val browser = browser(target)
        try {
            onMain { browser.search("France", null) }.get(15, TimeUnit.SECONDS)
            val results = onMain { browser.getSearchResult("France", 0, 10, null) }
                .get(15, TimeUnit.SECONDS).value!!
            assertTrue("Country search returned no results", results.isNotEmpty())
            assertEquals(AutoLibrary.radioCountryId("FR"), results.first().mediaId)
            assertTrue(results.first().mediaMetadata.isBrowsable == true)
            assertTrue(
                "Country search did not expose radio stations before generic TV matches",
                results.drop(1).any { it.mediaId.startsWith("radio-station:FR:") },
            )
        } finally {
            instrumentation.runOnMainSync { browser.release() }
            target.stopService(Intent(target, PlaybackService::class.java))
            app.radioDirectoryOverride = previousDirectory
        }
    }

    @Test fun mismatchedPackageNameCannotConnect() {
        try {
            browser(client)
            fail("A package name that does not belong to the calling UID was accepted")
        } catch (expected: java.util.concurrent.ExecutionException) {
            assertTrue(expected.cause is SecurityException)
        }
    }

    private fun browser(context: Context): MediaBrowser = MediaBrowser.Builder(context,
        SessionToken(target, ComponentName(target, PlaybackService::class.java)))
        .setApplicationLooper(Looper.getMainLooper()).buildAsync().get(10, TimeUnit.SECONDS)

    private fun isAaos35FocusRestricted(): Boolean =
        android.os.Build.VERSION.SDK_INT == 35 && target.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    private data class OfflineAudioFixture(val profileId: Long, val contentUri: Uri)

    private fun createOfflineAudioProfile(app: StreamLabApplication, profileName: String): OfflineAudioFixture {
        val resolver = target.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${profileName.replace(' ', '-')}-${System.nanoTime()}.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/StreamLabQA")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)) {
            "Cannot create local Android Auto audio fixture"
        }
        try {
            resolver.openOutputStream(uri, "w")?.use(::writeWavTone)
                ?: error("Cannot write local Android Auto audio fixture")
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) == 1) {
                "Cannot publish local Android Auto audio fixture"
            }
            val profileId = runBlocking {
                val id = app.repository.dao.saveProfile(ProfileEntity(name = profileName, type = ProfileType.MANUAL.name))
                app.repository.dao.saveSettings(ProfileSettingsEntity(id))
                app.repository.dao.upsertChannels(listOf(ChannelEntity(
                    profileId = id,
                    identity = "offline-audio",
                    name = profileName,
                    category = "Audio test",
                    tvgId = null,
                    tvgName = null,
                    logo = null,
                    encryptedUrl = app.secrets.encrypt(uri.toString()),
                    encryptedHeaders = app.secrets.encrypt("{}"),
                )))
                id
            }
            return OfflineAudioFixture(profileId, uri)
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    private fun deleteOfflineAudioProfile(app: StreamLabApplication, fixture: OfflineAudioFixture) {
        runBlocking { app.repository.deleteProfile(fixture.profileId) }
        target.contentResolver.delete(fixture.contentUri, null, null)
    }

    private fun writeWavTone(output: java.io.OutputStream) {
        val sampleRate = 16_000
        val sampleCount = sampleRate * 30
        val dataBytes = sampleCount * 2
        fun writeAscii(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
        fun writeLe16(value: Int) {
            output.write(value and 0xff)
            output.write((value ushr 8) and 0xff)
        }
        fun writeLe32(value: Int) {
            repeat(4) { index -> output.write((value ushr (8 * index)) and 0xff) }
        }
        writeAscii("RIFF")
        writeLe32(36 + dataBytes)
        writeAscii("WAVEfmt ")
        writeLe32(16)
        writeLe16(1)
        writeLe16(1)
        writeLe32(sampleRate)
        writeLe32(sampleRate * 2)
        writeLe16(2)
        writeLe16(16)
        writeAscii("data")
        writeLe32(dataBytes)
        repeat(sampleCount) { index ->
            val sample = (sin(2.0 * PI * 440.0 * index / sampleRate) * 2_000).toInt()
            writeLe16(sample)
        }
    }

    private fun <T> onMain(block: () -> ListenableFuture<T>): ListenableFuture<T> {
        val result = AtomicReference<ListenableFuture<T>>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }
}
