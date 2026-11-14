package app.cleanroom.streamlab

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.FetchGateway
import app.cleanroom.streamlab.data.CatalogRepository
import app.cleanroom.streamlab.data.ProfileType
import app.cleanroom.streamlab.data.Secrets
import app.cleanroom.streamlab.data.StreamLabDatabase
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@RunWith(AndroidJUnit4::class)
class XtreamLocalServerE2ETest {
    @Test fun controlledLocalServerReceivesExactXtreamRequest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, StreamLabDatabase::class.java).build()
        val server = MockWebServer()
        val playlist = """#EXTM3U
#EXTINF:-1 tvg-id="xtream.qa" group-title="QA Xtream",Xtream QA HLS
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
"""
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.enqueue(MockResponse.Builder().body(playlist).build())
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .build()
            val repository = CatalogRepository(context, database, Secrets(), MockServerGateway(server, client))
            val profileId = repository.createProfile(
                name = "Xtream local QA",
                type = ProfileType.XTREAM,
                source = "https://xtream.test/api",
                username = "qa user+1",
                password = "p@ss+ &",
            )
            try {
                assertEquals(1, repository.refreshProfile(profileId))
                val request = server.takeRequest()
                assertEquals(
                    "/api/get.php?username=qa+user%2B1&password=p%40ss%2B+%26&type=m3u_plus&output=ts",
                    request.url.encodedPath + "?" + request.url.encodedQuery,
                )
                val channel = repository.dao.channelsForProfile(profileId).single()
                assertEquals("Xtream QA", channel.name)
                assertEquals("QA Xtream", channel.category)
                assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", repository.playbackSource(channel.id).url)
            } finally {
                repository.deleteProfile(profileId)
            }
        } finally {
            server.close()
            database.close()
        }
    }

    private class MockServerGateway(private val server: MockWebServer, private val client: OkHttpClient) : FetchGateway {
        override fun fetch(url: String, maxBytes: Int): ByteArray {
            val original = URI(url)
            require(original.scheme == "https" && original.host == "xtream.test")
            val target = original.rawPath + original.rawQuery?.let { "?$it" }.orEmpty()
            val request = Request.Builder().url(server.url(target)).build()
            return client.newCall(request).execute().use { response ->
                require(response.isSuccessful)
                val bytes = response.body.bytes()
                require(bytes.size <= maxBytes)
                bytes
            }
        }
    }
}
