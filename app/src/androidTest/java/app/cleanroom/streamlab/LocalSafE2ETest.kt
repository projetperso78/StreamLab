package app.cleanroom.streamlab

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.data.ProfileType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSafE2ETest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun contentUriImportsLocalM3uThroughSafPath() = runBlocking {
        val app = instrumentation.targetContext.applicationContext as StreamLabApplication
        val profileName = "Local playlist"
        val fileName = "streamlab-saf-qa-${System.currentTimeMillis()}.m3u"
        app.repository.profiles.first()
            .filter { it.name == profileName }
            .forEach { app.repository.deleteProfile(it.id) }

        val fixtureUri = createSafFixture(fileName)
        try {
            val profileId = app.repository.createProfile(profileName, ProfileType.LOCAL_M3U, fixtureUri.toString())
            val count = app.repository.refreshProfile(profileId)
            assertEquals(1, count)

            val profile = app.repository.profiles.first().single { it.id == profileId }
            assertEquals(ProfileType.LOCAL_M3U.name, profile.type)
            assertTrue(profile.encryptedSource.isNotBlank())

            val channel = app.repository.dao.channelsForProfile(profileId).single()
            assertEquals("Local QA", channel.name)
            val source = app.repository.playbackSource(channel.id)
            assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", source.url)
        } finally {
            instrumentation.targetContext.contentResolver.delete(fixtureUri, null, null)
            app.repository.profiles.first()
                .filter { it.name == profileName }
                .forEach { app.repository.deleteProfile(it.id) }
        }
    }

    private fun createSafFixture(fileName: String): Uri {
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "audio/x-mpegurl")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri, "w")!!.use { output ->
            output.write(
                ("#EXTM3U\n" +
                    "#EXTINF:-1 tvg-id=\"local.qa\" group-title=\"Local QA\",Local QA HLS\n" +
                    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8\n").toByteArray(),
            )
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri
    }
}