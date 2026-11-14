package app.cleanroom.streamlab.services

import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** One entry of a home server folder. */
data class ShareEntry(val name: String, val url: String, val folder: Boolean, val size: Long)

/**
 * Browses a home WebDAV server (Synology, QNAP, Nextcloud, Freebox…): folders and video files,
 * so films play from the NAS without copying them to the phone.
 */
class WebDavClient(private val http: OkHttpClient) {
    fun authHeaders(username: String, password: String): Map<String, String> =
        if (username.isBlank()) emptyMap() else mapOf("Authorization" to Credentials.basic(username, password))

    /** Lists a folder (Depth 1): sub-folders first, then videos, by name. Throws on HTTP errors. */
    fun list(folderUrl: String, username: String, password: String): List<ShareEntry> {
        val url = if (folderUrl.endsWith('/')) folderUrl else "$folderUrl/"
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:displayname/></d:prop></d:propfind>"""
        val request = Request.Builder().url(url)
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "1")
            .apply { authHeaders(username, password).forEach { (k, v) -> header(k, v) } }
            .build()
        http.newCall(request).execute().use { response ->
            require(response.code != 401) { "Identifiant ou mot de passe refusé" }
            require(response.isSuccessful || response.code == 207) { "Le disque a répondu ${response.code}" }
            val xml = response.body?.string().orEmpty()
            return parse(xml, url)
        }
    }

    companion object {
        private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "m2ts", "wmv", "flv", "mpg", "mpeg", "3gp", "mp3", "m4a", "aac", "flac", "ogg", "wav")

        fun isPlayable(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in videoExtensions

        /** Parses a WebDAV multistatus answer; the folder itself is left out. */
        fun parse(xml: String, folderUrl: String): List<ShareEntry> {
            val base = URI(folderUrl)
            // No DTD at all: entities are how a hostile server could make the parser read local
            // files. A WebDAV answer never needs one. (Checked by hand: Android's parser has no
            // "disallow-doctype-decl" feature.)
            require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) { "Réponse du disque refusée" }
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(xml)))
            val responses = doc.getElementsByTagNameNS("DAV:", "response")
            val entries = mutableListOf<ShareEntry>()
            for (i in 0 until responses.length) {
                val response = responses.item(i) as org.w3c.dom.Element
                fun text(tag: String) = response.getElementsByTagNameNS("DAV:", tag).item(0)?.textContent?.trim()
                val link = text("href") ?: continue
                val absolute = base.resolve(link).toString()
                if (absolute.trimEnd('/') == folderUrl.trimEnd('/')) continue
                val folder = response.getElementsByTagNameNS("DAV:", "collection").length > 0
                val shown = text("displayname")?.ifBlank { null } ?: URLDecoder.decode(link.trimEnd('/').substringAfterLast('/'), "UTF-8")
                val size = text("getcontentlength")?.toLongOrNull() ?: 0L
                if (folder || isPlayable(shown)) entries += ShareEntry(shown, absolute, folder, size)
            }
            return entries.sortedWith(compareBy<ShareEntry>({ !it.folder }, { it.name.lowercase() }))
        }
    }
}
