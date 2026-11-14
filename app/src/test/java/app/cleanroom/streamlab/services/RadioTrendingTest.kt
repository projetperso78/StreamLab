package app.cleanroom.streamlab.services

import app.cleanroom.streamlab.core.FetchGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioTrendingTest {
    private var requested = ""
    private val directory = RadioBrowserDirectory(object : FetchGateway {
        override fun fetch(url: String, maxBytes: Int): ByteArray {
            requested = url
            return """[
              {"stationuuid":"a","name":"Rising FM","url_resolved":"https://a.example/live","countrycode":"MA","lastcheckok":1},
              {"stationuuid":"b","name":"Elsewhere","url_resolved":"https://b.example/live","countrycode":"FR","lastcheckok":1},
              {"stationuuid":"c","name":"Broken","url_resolved":"https://c.example/live","countrycode":"MA","lastcheckok":0}
            ]""".toByteArray()
        }
    })

    @Test fun asksTheDirectoryForWhatIsRisingNowInThatCountry() = runBlocking {
        val list = directory.trending("ma")
        assertTrue(requested.contains("countrycode=MA"))
        assertTrue(requested.contains("order=clicktrend"))
        assertTrue(requested.contains("reverse=true"))
        assertEquals(listOf("Rising FM"), list.map { it.name })
    }

    @Test fun limitIsBounded() {
        assertTrue(directory.trendingUrl("FR", 10_000).endsWith("limit=100"))
    }
}
