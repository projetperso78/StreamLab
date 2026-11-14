package app.cleanroom.streamlab.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * A Chromecast cannot read a file stored on the phone, so while it casts, the phone serves
 * that one file on the home Wi-Fi: http://<phone-ip>:<port>/<random-token>. Only the file
 * currently published is reachable, the address changes with every file, and nothing else
 * on the phone is exposed.
 */
object LocalCastServer {
    private var server: ServerSocket? = null
    @Volatile private var published: Pair<String, Uri>? = null
    @Volatile private var mime: String = "video/mp4"
    private lateinit var appContext: Context

    /** Address the TV can fetch, or null when the phone is not on Wi-Fi. */
    @Synchronized
    fun publish(context: Context, uri: Uri, contentType: String): String? {
        appContext = context.applicationContext
        val ip = wifiAddress(appContext) ?: return null
        val socket = server ?: ServerSocket(0).also { server = it; accept(it) }
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        published = token to uri
        mime = contentType
        return "http://$ip:${socket.localPort}/$token"
    }

    private fun accept(socket: ServerSocket) = thread(name = "StreamLabCastServer", isDaemon = true) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            thread(isDaemon = true) { runCatching { serve(client) }; runCatching { client.close() } }
        }
    }

    private fun serve(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.ISO_8859_1))
        val requestLine = reader.readLine() ?: return
        val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
        val out = client.getOutputStream()
        val parts = requestLine.split(' ')
        val (token, uri) = published ?: return status(out, "404 Not Found")
        if (parts.size < 2 || parts[1].trimStart('/') != token) return status(out, "404 Not Found")
        val head = parts[0] == "HEAD"
        val descriptor = appContext.contentResolver.openFileDescriptor(uri, "r") ?: return status(out, "404 Not Found")
        descriptor.use { fd ->
            val total = fd.statSize
            val range = headers.firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                ?.substringAfter('=')?.split('-')
            val start = range?.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = range?.getOrNull(1)?.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
            if (start >= total || start > end) return status(out, "416 Range Not Satisfiable")
            val length = end - start + 1
            val statusLine = if (range != null) "206 Partial Content" else "200 OK"
            val responseHeaders = buildString {
                append("HTTP/1.1 $statusLine\r\n")
                append("Content-Type: $mime\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Length: $length\r\n")
                if (range != null) append("Content-Range: bytes $start-$end/$total\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(responseHeaders.toByteArray(Charsets.ISO_8859_1))
            if (head) return out.flush()
            java.io.FileInputStream(fd.fileDescriptor).use { input ->
                input.channel.position(start)
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }
            out.flush()
        }
    }

    private fun status(out: OutputStream, line: String) {
        out.write("HTTP/1.1 $line\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    /** The phone's IPv4 address on Wi-Fi or Ethernet; null on mobile data only. */
    private fun wifiAddress(context: Context): String? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return manager.allNetworks.firstNotNullOfOrNull { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return@firstNotNullOfOrNull null
            manager.getLinkProperties(network)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
    }
}
