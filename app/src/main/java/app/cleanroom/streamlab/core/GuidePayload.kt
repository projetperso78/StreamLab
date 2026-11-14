package app.cleanroom.streamlab.core

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Opens XMLTV payloads served either as plain XML or gzip-compressed XML. */
object GuidePayload {
    fun open(bytes: ByteArray, source: String): InputStream {
        require(bytes.isNotEmpty()) { "Empty guide" }
        val gzipMagic = bytes.size >= 2 &&
            (bytes[0].toInt() and 0xff) == 0x1f &&
            (bytes[1].toInt() and 0xff) == 0x8b
        val gzipName = source.substringBefore('?').substringBefore('#').endsWith(".gz", ignoreCase = true)
        val raw = ByteArrayInputStream(bytes)
        return if (gzipMagic || gzipName) GZIPInputStream(raw) else raw
    }
}
