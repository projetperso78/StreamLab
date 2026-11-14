package app.cleanroom.streamlab.services

import android.content.Context
import android.util.AtomicFile
import app.cleanroom.streamlab.core.SourcePolicy
import java.io.File
import java.net.URI

/** User-chosen local servers are allowed only at their exact origin. */
object ServerAddressPolicy {
    fun validate(raw: String): String {
        require(raw.length in 8..2048 && raw.none { it.isISOControl() }) { "Adresse de serveur invalide" }
        val uri = runCatching { URI(raw.trim()) }.getOrElse { throw IllegalArgumentException("Adresse de serveur invalide") }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Indique une adresse complète avec https://")
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.port in -1..65535) {
            "Saisis l’adresse du serveur sans identifiants, paramètres ou fragment"
        }
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        val local = host.endsWith(".local") || (octets.size == 4 && octets.all { it in 0..255 } &&
            (octets[0] == 10 || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31)))
        require(uri.scheme.equals("https", true) || (uri.scheme.equals("http", true) && local)) {
            "Utilise HTTPS ; HTTP est réservé à ton serveur sur le réseau local"
        }
        require(host !in setOf("localhost", "127.0.0.1", "0.0.0.0", "[::1]") && !host.endsWith(".localhost")) {
            "Adresse de serveur non autorisée"
        }
        return uri.toASCIIString()
    }

    fun requireNavigation(raw: String, configuredHome: String) {
        val target = URI(raw)
        val home = URI(configuredHome)
        if (target.scheme.equals(home.scheme, true) && target.host.equals(home.host, true) && target.port == home.port) {
            require(target.rawUserInfo == null)
            validate(configuredHome)
        } else SourcePolicy.requireRemote(raw)
    }
}

class ServerEndpoints(private val context: Context) {
    private fun file(id: String): AtomicFile {
        require(id in CONFIGURABLE)
        return AtomicFile(File(context.filesDir, "server-$id.txt"))
    }
    fun saved(id: String): String? = runCatching { file(id).openRead().bufferedReader().use { it.readText() } }
        .getOrNull()?.let { runCatching { ServerAddressPolicy.validate(it) }.getOrNull() }
    fun save(id: String, raw: String) {
        val value = ServerAddressPolicy.validate(raw)
        val file = file(id)
        val output = file.startWrite()
        try { output.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }
    fun home(service: ExternalService): String =
        if (service.id in CONFIGURABLE) saved(service.id) ?: service.website.toASCIIString()
        else service.website.toASCIIString()
    companion object { val CONFIGURABLE = setOf("plex", "jellyfin") }
}
