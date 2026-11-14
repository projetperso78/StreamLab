package app.cleanroom.streamlab

import app.cleanroom.streamlab.core.SourcePolicy
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class SecureNetwork {
    private val publicDns = Dns { hostname ->
        val addresses = Dns.SYSTEM.lookup(hostname)
        // A home server the user added may resolve to a local address; nothing else may.
        val home = SourcePolicy.homeOrigins.any { it.substringAfter("://").substringBeforeLast(':') == hostname.lowercase() }
        require(addresses.isNotEmpty() && (home || addresses.all(::isPublic))) { "Unsafe destination" }
        addresses
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(publicDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            SourcePolicy.requireRemote(chain.request().url.toString())
            chain.proceed(chain.request())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaFetchClient: OkHttpClient = OkHttpClient.Builder()
        .dns(publicDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            SourcePolicy.requireMediaRemote(chain.request().url.toString())
            chain.proceed(chain.request())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Player-only client: public HTTP/HTTPS media is allowed; every resolved hop must remain public. */
    val playbackClient: OkHttpClient = OkHttpClient.Builder()
        .dns(publicDns)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            SourcePolicy.requireMediaRemote(chain.request().url.toString())
            chain.proceed(chain.request())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        // A live radio or TV stream is one endless response: a whole-call limit would cut it
        // every N seconds, and each reconnect replays the station's pre-roll ad. Only a
        // stalled stream (no byte for 20 s) counts as a failure.
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    fun fetch(raw: String, limit: Int, headers: Map<String, String> = emptyMap()): ByteArray =
        fetchInternal(raw, limit, headers, media = false)

    fun fetchMedia(raw: String, limit: Int, headers: Map<String, String> = emptyMap()): ByteArray =
        fetchInternal(raw, limit, headers, media = true)

    private fun fetchInternal(raw: String, limit: Int, headers: Map<String, String>, media: Boolean): ByteArray {
        require(limit in 1..32_000_000)
        fun validate(value: String) = if (media) SourcePolicy.requireMediaRemote(value) else SourcePolicy.requireRemote(value)
        val http = if (media) mediaFetchClient else client
        var url = validate(raw)
        repeat(4) { hop ->
            val request = Request.Builder().url(url).apply {
                headers.forEach { (key, value) ->
                    if (key in setOf("User-Agent", "Referer", "Origin") && value.length <= 2048 && value.none(Char::isISOControl)) header(key, value)
                }
            }.build()
            http.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    require(hop < 3) { "Too many redirects" }
                    val target = response.header("Location") ?: error("Missing redirect")
                    url = validate(URI(url).resolve(target).toString())
                } else {
                    require(response.isSuccessful) { "HTTP " + response.code }
                    val body = response.body ?: error("Empty response")
                    require(body.contentLength() <= limit) { "Source exceeds size limit" }
                    body.byteStream().use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            require(output.size() + n <= limit) { "Source exceeds size limit" }
                            output.write(buffer, 0, n)
                        }
                        return output.toByteArray()
                    }
                }
            }
        }
        error("Too many redirects")
    }

    private fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 255
            val b = bytes[1].toInt() and 255
            if (a == 0 || a >= 224 || (a == 100 && b in 64..127) ||
                (a == 192 && b == 0) || (a == 198 && b in 18..19)) return false
        } else if ((bytes[0].toInt() and 0xfe) == 0xfc) return false
        return true
    }
}
