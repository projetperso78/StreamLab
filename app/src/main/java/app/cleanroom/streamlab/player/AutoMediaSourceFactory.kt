package app.cleanroom.streamlab.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.AssetDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import app.cleanroom.streamlab.StreamLabApplication
import app.cleanroom.streamlab.core.HeaderPolicy
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.data.PlaybackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Resolves opaque car item IDs inside the app and keeps source URLs out of session timelines. */
@UnstableApi
internal class AutoMediaSourceFactory(
    private val context: Context,
    private val resolvePersisted: suspend (Long) -> PlaybackSource,
) : MediaSource.Factory {
    private val sources = LinkedHashMap<String, PlaybackSource>()

    @Synchronized fun put(id: String, source: PlaybackSource) {
        if (sources.size >= MAX_CACHED_SOURCES) sources.remove(sources.keys.first())
        sources[id] = source
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val source = synchronized(this) { sources[mediaItem.mediaId] } ?: resolveFromCatalog(mediaItem.mediaId)
        val headers = HeaderPolicy.sanitize(source.headers)
        val actual = when {
            source.url == "fixture://demo.mp4" -> {
                ProgressiveMediaSource.Factory { AssetDataSource(context) }
                    .createMediaSource(MediaItem.fromUri("asset:///demo.mp4"))
            }
            source.url.startsWith("content://", ignoreCase = true) -> {
                ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
                    .createMediaSource(MediaItem.fromUri(source.url))
            }
            else -> {
                val factory = OkHttpDataSource.Factory((context.applicationContext as StreamLabApplication).network.playerClient())
                    .setDefaultRequestProperties(headers)
                val actualItem = MediaItem.fromUri(source.url)
                if (source.url.substringBefore('?').endsWith(".m3u8", true))
                    HlsMediaSource.Factory(factory).createMediaSource(actualItem)
                else ProgressiveMediaSource.Factory(factory).createMediaSource(actualItem)
            }
        }
        return PublicMediaSource(actual, mediaItem)
    }

    private fun resolveFromCatalog(mediaId: String): PlaybackSource {
        val channelId = OpaqueMediaId.channelId(mediaId) ?: throw IllegalArgumentException("Unknown media item")
        val source = try {
            runBlocking(Dispatchers.IO) { resolvePersisted(channelId) }
        } catch (failure: Exception) {
            throw IllegalArgumentException("Unavailable media item", failure)
        }
        put(mediaId, source)
        return source
    }

    fun mask(source: MediaSource, publicItem: MediaItem): MediaSource = PublicMediaSource(source, publicItem)

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS)
    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = this
    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = this

    private class PublicMediaSource(source: MediaSource, private val publicItem: MediaItem) : WrappingMediaSource(source) {
        override fun getMediaItem(): MediaItem = publicItem
        override fun getInitialTimeline(): Timeline? = super.getInitialTimeline()?.let { PublicTimeline(it, publicItem) }
        override fun onChildSourceInfoRefreshed(timeline: Timeline) {
            refreshSourceInfo(PublicTimeline(timeline, publicItem))
        }
    }

    private class PublicTimeline(timeline: Timeline, private val publicItem: MediaItem) : ForwardingTimeline(timeline) {
        override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window {
            val result = super.getWindow(windowIndex, window, defaultPositionProjectionUs)
            result.mediaItem = publicItem
            result.manifest = null
            return result
        }
    }

    private companion object { const val MAX_CACHED_SOURCES = 200 }
}
