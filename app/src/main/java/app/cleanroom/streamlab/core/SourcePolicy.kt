package app.cleanroom.streamlab.core

import java.net.IDN
import java.net.URI

object SourcePolicy {
    /**
     * Home servers the user added themself (scheme://host:port, lower case). Only these exact
     * origins may use a local-network address; everything else stays blocked, so a playlist
     * cannot reach the router or other devices at home.
     */
    @Volatile var homeOrigins: Set<String> = emptySet()

    fun origin(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        val port = if (uri.port != -1) uri.port else if (scheme == "https") 443 else 80
        "$scheme://$host:$port"
    }.getOrNull()

    fun isHomeOrigin(raw: String): Boolean = origin(raw)?.let { it in homeOrigins } == true

    fun requireRemote(raw: String): String = requirePublicRemote(raw, setOf("https"), "Only HTTPS sources are supported")

    fun requireMediaRemote(raw: String): String =
        requirePublicRemote(raw, setOf("http", "https"), "Only HTTP/HTTPS media sources are supported")

    fun requireLocalContent(raw: String): String {
        require(raw.length in 10..4096 && raw.none { it.isISOControl() }) { "Invalid local media address" }
        val uri = try { URI(raw.trim()) } catch (_: Exception) { throw IllegalArgumentException("Invalid local media address") }
        require(uri.scheme?.lowercase() == "content" && !uri.rawAuthority.isNullOrBlank()) { "Choose a local file" }
        return uri.toASCIIString()
    }

    private fun requirePublicRemote(raw: String, schemes: Set<String>, schemeMessage: String): String {
        require(raw.length in 8..4096 && raw.none { it.isISOControl() }) { "Invalid address" }
        val uri = try { URI(raw.trim()) } catch (_: Exception) { throw IllegalArgumentException("Invalid address") }
        require(uri.scheme?.lowercase() in schemes) { schemeMessage }
        require(uri.rawUserInfo == null && uri.host != null && uri.port in -1..65535) { "Invalid address" }
        val host = IDN.toASCII(uri.host).lowercase().trimEnd('.')
        if (isHomeOrigin(raw) && "http" in schemes) return uri.toASCIIString()
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local")) { "Local network addresses are blocked" }
        require(!isPrivateAddress(host)) { "Private network addresses are blocked" }
        return uri.toASCIIString()
    }

    private fun isPrivateAddress(host: String): Boolean {
        if (host == "::1" || host == "[::1]" || host.startsWith("fc", true) || host.startsWith("fd", true) || host.startsWith("fe80", true)) return true
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 0 || octets[0] == 10 || octets[0] == 127 || octets[0] >= 224 ||
            (octets[0] == 169 && octets[1] == 254) || (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }
}
