package app.cleanroom.streamlab

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cleanroom.streamlab.core.FetchGateway
import app.cleanroom.streamlab.services.RadioBrowserDirectory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioDirectoryTest {
    @Test fun countriesAndStationsKeepIconsAndWorkingSources() {
        val gateway = object : FetchGateway {
            override fun fetch(url: String, maxBytes: Int): ByteArray = when {
                url.contains("countrycodes") -> """[{"name":"FR","stationcount":123},{"name":"US","stationcount":456}]""".toByteArray()
                else -> """[
                    {"stationuuid":"ok-1","name":"Radio Test","countrycode":"FR","url":"http://example.org/stream","url_resolved":"https://example.org/live.mp3","favicon":"https://example.org/logo.png","codec":"MP3","bitrate":128,"votes":42,"tags":"news,talk","lastcheckok":1},
                    {"stationuuid":"bad-1","name":"Broken","countrycode":"FR","url_resolved":"https://example.org/broken.mp3","favicon":"","codec":"MP3","bitrate":64,"votes":0,"lastcheckok":0}
                ]""".trimIndent().toByteArray()
            }
        }
        val directory = RadioBrowserDirectory(gateway)
        val countries = kotlinx.coroutines.runBlocking { directory.countries() }
        assertEquals(listOf("FR", "US"), countries.map { it.code })
        assertTrue(countries.first().flag.isNotBlank())

        val stations = kotlinx.coroutines.runBlocking { directory.stations("FR") }
        assertEquals(1, stations.size)
        assertEquals("Radio Test", stations.single().name)
        assertEquals("https://example.org/live.mp3", stations.single().streamUrl)
        assertEquals("https://example.org/logo.png", stations.single().favicon)
        assertEquals("MP3", stations.single().codec)
        assertEquals(128, stations.single().bitrate)
    }
}
