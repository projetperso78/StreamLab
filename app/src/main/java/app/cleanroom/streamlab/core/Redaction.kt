package app.cleanroom.streamlab.core

object Redaction {
    private val url = Regex("(?i)https?://[^\\s<>\"']+")
    private val credential = Regex("(?i)(authorization|password|passwd|token|api[_-]?key|secret|mac|user(name)?|cookie)\\s*[:=]\\s*[^,;\\s]+")
    fun diagnostic(message: String?): String {
        if (message == null) return "Playback failed"
        return credential.replace(url.replace(message) { match ->
            val value = match.value
            val uri = runCatching { java.net.URI(value) }.getOrNull()
            if (uri?.host == null) "[redacted address]" else "${uri.scheme}://${uri.host}/[redacted]"
        }) { match -> "${match.groupValues[1]}=[redacted]" }.take(300)
    }
}
