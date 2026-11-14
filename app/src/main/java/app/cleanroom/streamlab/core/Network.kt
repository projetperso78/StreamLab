package app.cleanroom.streamlab.core

import app.cleanroom.streamlab.SecureNetwork
import okhttp3.OkHttpClient

interface FetchGateway {
    fun fetch(url: String, maxBytes: Int): ByteArray
    fun fetchMedia(url: String, maxBytes: Int): ByteArray = fetch(url, maxBytes)
    fun fetchWithHeaders(url: String, maxBytes: Int, headers: Map<String, String>): ByteArray = fetch(url, maxBytes)
}

class Network : FetchGateway {
    private val secure = SecureNetwork()
    fun playerClient(): OkHttpClient = secure.playbackClient
    override fun fetch(url: String, maxBytes: Int): ByteArray = secure.fetch(url, maxBytes)
    override fun fetchMedia(url: String, maxBytes: Int): ByteArray = secure.fetchMedia(url, maxBytes)
    override fun fetchWithHeaders(url: String, maxBytes: Int, headers: Map<String, String>): ByteArray = secure.fetch(url, maxBytes, headers)
}
