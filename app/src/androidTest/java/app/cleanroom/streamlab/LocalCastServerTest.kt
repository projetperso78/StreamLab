package app.cleanroom.streamlab

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.cast.LocalCastServer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A TV fetches a phone video from StreamLab: whole file, a byte range (seeking) and a wrong address. */
@RunWith(AndroidJUnit4::class)
class LocalCastServerTest {
    @Test(timeout = 60_000)
    fun servesOnlyThePublishedFileWithRanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "cast-qa.mp4").apply { writeBytes(ByteArray(1000) { (it % 251).toByte() }) }
        val url = LocalCastServer.publish(context, Uri.fromFile(file), "video/mp4")
        assumeTrue("needs a Wi-Fi network (the emulator has one)", url != null)

        val whole = URL(url).openConnection() as HttpURLConnection
        assertEquals(200, whole.responseCode)
        assertEquals(1000, whole.inputStream.use { it.readBytes() }.size)

        val part = URL(url).openConnection() as HttpURLConnection
        part.setRequestProperty("Range", "bytes=10-19")
        assertEquals(206, part.responseCode)
        assertEquals("bytes 10-19/1000", part.getHeaderField("Content-Range"))
        assertArrayEquals(file.readBytes().copyOfRange(10, 20), part.inputStream.use { it.readBytes() })

        val guess = URL(url!!.substringBeforeLast('/') + "/not-the-token").openConnection() as HttpURLConnection
        assertEquals(404, guess.responseCode)
        file.delete()
    }
}
