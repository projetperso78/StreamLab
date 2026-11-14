package app.cleanroom.streamlab

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import app.cleanroom.streamlab.core.HeaderPolicy
import app.cleanroom.streamlab.data.CatalogRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Which channels are out of service right now. Public playlists age fast (on 13 Nov 2026,
 * 54 of the 201 iptv-org France channels did not answer): dead ones go to the end of the
 * list, greyed out, and zapping skips them. A channel that plays again is restored.
 */
object ChannelHealth {
    private const val STORE = "streamlab_channel_health"
    private const val DEAD = "dead"
    /** Bumped on every change so lists redraw. */
    val version = mutableIntStateOf(0)

    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun deadIds(context: Context): Set<Long> =
        prefs(context).getStringSet(DEAD, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun mark(context: Context, channelId: Long, alive: Boolean) {
        val current = prefs(context).getStringSet(DEAD, emptySet()).orEmpty().toMutableSet()
        val changed = if (alive) current.remove(channelId.toString()) else current.add(channelId.toString())
        if (changed) { prefs(context).edit().putStringSet(DEAD, current).apply(); version.intValue++ }
    }

    /** True when at least one of the channel's addresses (main or backup) answers with media. */
    suspend fun probe(app: StreamLabApplication, repository: CatalogRepository, channelId: Long): Boolean = withContext(Dispatchers.IO) {
        val source = runCatching { repository.playbackSource(channelId) }.getOrNull() ?: return@withContext false
        if (!source.url.startsWith("http", ignoreCase = true)) return@withContext true
        val client = app.network.playerClient().newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
        source.allUrls.any { url ->
            runCatching {
                val request = Request.Builder().url(url).apply { HeaderPolicy.sanitize(source.headers).forEach { (k, v) -> header(k, v) } }.build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful && (response.body?.source()?.request(256) == true)
                }
            }.getOrDefault(false)
        }
    }

    /** Checks every channel, six at a time; [onProgress] gets (done, total). Returns the number out of service. */
    suspend fun checkAll(app: StreamLabApplication, repository: CatalogRepository, channelIds: List<Long>, onProgress: (Int, Int) -> Unit): Int = coroutineScope {
        val gate = Semaphore(6)
        var done = 0
        val results = channelIds.map { id ->
            async {
                gate.withPermit {
                    val alive = probe(app, repository, id)
                    withContext(Dispatchers.Main) { mark(app, id, alive); done++; onProgress(done, channelIds.size) }
                    alive
                }
            }
        }.awaitAll()
        results.count { !it }
    }
}
