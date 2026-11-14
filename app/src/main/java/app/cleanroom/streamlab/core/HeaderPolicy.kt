package app.cleanroom.streamlab.core

object HeaderPolicy {
    private val name = Regex("[A-Za-z0-9-]{1,64}")
    private val forbidden = setOf("host", "connection", "content-length", "transfer-encoding", "proxy-authorization", "proxy-connection")
    fun valid(key: String, value: String): Boolean =
        name.matches(key) && key.lowercase() !in forbidden &&
            value.length <= 2048 && value.none { it.isISOControl() }
    fun sanitize(headers: Map<String, String>): Map<String, String> =
        headers.filter { (key, value) -> valid(key, value) }.toList().take(24).toMap()
}