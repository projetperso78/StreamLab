package app.cleanroom.streamlab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import app.cleanroom.streamlab.core.TextMatch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Channel logos from the playlist (tvg-logo), shrunk once and kept in memory and on disk, so a
 * list of hundreds of channels stays smooth and works offline after the first visit.
 */
object ChannelLogos {
    private const val MAX_BYTES = 512_000
    private const val TARGET_PX = 160
    private val memory = LruCache<Long, Bitmap>(200)
    private val missing = java.util.Collections.synchronizedSet(HashSet<Long>())
    private val downloads = Semaphore(4)

    fun cached(channelId: Long): Bitmap? = memory.get(channelId)

    /** Tile behind a logo: white, or dark for white logos on a transparent background. */
    const val LIGHT_TILE = 0xFFFFFFFF.toInt()
    const val DARK_TILE = 0xFF1C262C.toInt()
    fun tileColor(logo: Bitmap): Int = if (isWhiteOnTransparent(logo)) DARK_TILE else LIGHT_TILE

    internal fun isWhiteOnTransparent(logo: Bitmap): Boolean {
        val step = maxOf(1, maxOf(logo.width, logo.height) / 48)
        var transparent = 0; var visible = 0; var light = 0
        for (y in 0 until logo.height step step) for (x in 0 until logo.width step step) {
            val c = logo.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 40) { transparent++; continue }
            visible++
            val lum = 0.2126 * android.graphics.Color.red(c) + 0.7152 * android.graphics.Color.green(c) + 0.0722 * android.graphics.Color.blue(c)
            if (lum > 200) light++
        }
        val total = transparent + visible
        return total > 0 && visible > 0 && transparent > total / 5 && light > visible * 0.7
    }

    /** The channel's logo, or null when it has none or it cannot be read (the initials tile stays). */
    suspend fun load(app: StreamLabApplication, channelId: Long): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(channelId)?.let { return@withContext it }
        if (channelId in missing) return@withContext null
        // One load per channel at a time: the phone list and the car list ask for the same logos,
        // and a file read while another load was still writing it used to drop the logo.
        locks.getOrPut(channelId) { Mutex() }.withLock {
            memory.get(channelId)?.let { return@withLock it }
            if (channelId in missing) return@withLock null
            // This copy's logo first, then the logos of its twins (same channel from another
            // playlist): some logo addresses refuse the download (HTTP 403) while a twin's works.
            val own = logoUrl(app, channelId)
            var networkFailed = false
            var tried = 0
            fun fromFile(url: String): Bitmap? {
                tried++
                val file = File(File(app.cacheDir, "channel-logos").apply { mkdirs() }, sha(url) + ".png")
                if (file.isFile) BitmapFactory.decodeFile(file.path)?.let { return it } ?: file.delete()
                return null
            }
            suspend fun fetch(url: String): Bitmap? {
                fromFile(url)?.let { return it }
                val file = File(File(app.cacheDir, "channel-logos"), sha(url) + ".png")
                val bytes = runCatching { downloads.withPermit { app.network.fetchMedia(url, MAX_BYTES) } }
                    .onFailure { if (it !is IllegalArgumentException) networkFailed = true }.getOrNull() ?: return null
                return decodeSmall(bytes)?.also { small ->
                    runCatching {
                        val part = File(file.path + ".part")
                        part.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        if (!part.renameTo(file)) part.delete()
                    }
                }
            }
            var bitmap = own?.let { fetch(it) }
            // Twins are looked up only when this copy has no logo or its logo fails.
            if (bitmap == null) {
                for (twin in runCatching { twinsWithLogo(app, channelId) }.getOrDefault(emptyList())) {
                    val url = logoUrl(app, twin)?.takeIf { it != own } ?: continue
                    bitmap = fetch(url)
                    if (bitmap != null) break
                }
            }
            if (bitmap == null && tried == 0) { missing += channelId; return@withLock null }
            // A network failure is not remembered: the logo is tried again next time.
            if (bitmap == null && networkFailed) return@withLock null
            if (bitmap == null) missing += channelId else memory.put(channelId, bitmap)
            bitmap
        }
    }
    private val locks = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()

    // Logo addresses are stored encrypted with the Android keystore, which refuses too many
    // operations at once: with a few hundred channels, parallel decryption failed at random.
    private val decryptLock = Mutex()
    private val urls = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private suspend fun logoUrl(app: StreamLabApplication, channelId: Long): String? {
        urls[channelId]?.let { return it }
        return decryptLock.withLock { runCatching { app.repository.logoUrl(channelId) }.getOrNull() }?.also { urls[channelId] = it }
    }

    /**
     * The same channel often comes several times (other playlist, backup line) and only some copies
     * carry a working logo; the car list may show any copy, so a copy borrows its twins' logos.
     */
    private suspend fun twinsWithLogo(app: StreamLabApplication, channelId: Long): List<Long> = twinsLock.withLock {
        val now = System.currentTimeMillis()
        val index = siblings?.takeIf { now - siblingsAt < 60_000 } ?: run {
            val rows = app.repository.channels.first()
            val logos = rows.filter { it.logo != null }
            val byName = logos.groupBy({ TextMatch.normalize(it.name) }, { it.id })
            val byTvgId = logos.filter { !it.tvgId.isNullOrBlank() }.groupBy({ it.tvgId!!.lowercase() }, { it.id })
            rows.associate { row ->
                row.id to (byName[TextMatch.normalize(row.name)].orEmpty() + row.tvgId?.lowercase()?.let { byTvgId[it] }.orEmpty()).distinct()
            }
                .also { siblings = it; siblingsAt = now }
        }
        index[channelId].orEmpty().filter { it != channelId }
    }
    private val twinsLock = Mutex()
    @Volatile private var siblings: Map<Long, List<Long>>? = null
    @Volatile private var siblingsAt = 0L

    private fun decodeSmall(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        // Tiny images look broken once enlarged: the initials tile is cleaner.
        if (bounds.outWidth < 32 || bounds.outHeight < 16) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_PX && bounds.outHeight / (sample * 2) >= TARGET_PX / 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = TARGET_PX.toFloat() / maxOf(decoded.width, decoded.height)
        return if (scale >= 1f) decoded
        else Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}

/** Channel logo on a light tile, or the initials tile while loading or when there is none. */
@Composable
fun ChannelLogo(channelId: Long, name: String, hasLogo: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as StreamLabApplication
    var bitmap by remember(channelId) { mutableStateOf(ChannelLogos.cached(channelId)) }
    if (hasLogo) LaunchedEffect(channelId) { if (bitmap == null) bitmap = ChannelLogos.load(app, channelId) }
    val image = bitmap
    if (image == null) {
        MonogramTile(name, size, modifier)
        return
    }
    val picture = remember(image) { image.asImageBitmap() }
    val tile = remember(image) { androidx.compose.ui.graphics.Color(ChannelLogos.tileColor(image)) }
    Surface(modifier.size(size), shape = RoundedCornerShape(size / 4), color = tile) {
        Image(picture, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(size / 10))
    }
}
