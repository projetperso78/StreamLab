package app.cleanroom.streamlab.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

data class ParsedProgramme(val channel: String, val title: String, val start: Long, val stop: Long)
data class ParsedGuide(val channelNames: Map<String, String>, val programmes: List<ParsedProgramme>)

class XmlTvParser(private val maxBytes: Long = 16_000_000, private val maxProgrammes: Int = 100_000) {
    fun parse(stream: InputStream): ParsedGuide {
        val payload = readBounded(stream)
        val xmlForPolicy = decodeForPolicy(payload).uppercase(Locale.ROOT)
        require(!xmlForPolicy.contains("<!DOCTYPE") && !xmlForPolicy.contains("<!ENTITY")) {
            "DTD and XML entities are not allowed"
        }
        val factory = SAXParserFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { isXIncludeAware = false }
            isNamespaceAware = false
        }
        val names = mutableMapOf<String, String>()
        val programmes = ArrayList<ParsedProgramme>()
        val handler = object : DefaultHandler() {
            var channelId: String? = null
            var current: Triple<String, Long, Long>? = null
            var titleAdded = false
            var element = ""
            val text = StringBuilder()
            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                element = qName
                text.clear()
                when (qName) {
                    "channel" -> channelId = attrs.getValue("id")?.take(256)
                    "programme" -> {
                        titleAdded = false
                        val start = parseTime(attrs.getValue("start"))
                        val stop = parseTime(attrs.getValue("stop"))
                        current = if (start != null && stop != null && stop > start) Triple(attrs.getValue("channel")?.take(256) ?: "", start, stop) else null
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (text.length < 2048) text.append(ch, start, length.coerceAtMost(2048 - text.length))
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                val value = text.toString().trim().take(512)
                if (qName == "display-name" && channelId != null && value.isNotBlank()) names.putIfAbsent(channelId!!, value)
                if (qName == "title" && current != null && !titleAdded && value.isNotBlank()) {
                    titleAdded = true
                    require(programmes.size < maxProgrammes) { "Guide exceeds programme limit" }
                    val p = current!!
                    programmes += ParsedProgramme(p.first, value, p.second, p.third)
                }
                if (qName == "programme") current = null
                if (qName == "channel") channelId = null
                element = ""
                text.clear()
            }
        }
        val reader = factory.newSAXParser().xmlReader
        runCatching { reader.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        reader.contentHandler = handler
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw IllegalArgumentException("External XML entity blocked") }
        reader.parse(InputSource(ByteArrayInputStream(payload)))
        return ParsedGuide(names, programmes)
    }

    private fun readBounded(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = 0L
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            count += n
            require(count <= maxBytes) { "Guide exceeds size limit" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun decodeForPolicy(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, Charsets.UTF_16BE)
        else -> String(bytes, Charsets.UTF_8)
    }

    private fun parseTime(raw: String?): Long? = runCatching {
        require(raw != null && raw.length >= 14)
        val date = LocalDateTime.parse(raw.substring(0, 14), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val offset = raw.drop(14).trim().ifBlank { "+0000" }
        val zone = ZoneOffset.of(offset.substring(0, 3) + ":" + offset.substring(3, 5))
        OffsetDateTime.of(date, zone).toInstant().toEpochMilli()
    }.getOrNull()
}
