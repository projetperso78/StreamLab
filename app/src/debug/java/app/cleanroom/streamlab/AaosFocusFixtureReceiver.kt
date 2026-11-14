package app.cleanroom.streamlab

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import app.cleanroom.streamlab.data.ChannelEntity
import app.cleanroom.streamlab.data.ProfileEntity
import app.cleanroom.streamlab.data.ProfileSettingsEntity
import app.cleanroom.streamlab.data.ProfileType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** Debug-only, shell-protected seed/cleanup fixture for validating playback under the real app UID. */
class AaosFocusFixtureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_SEED && action != ACTION_CLEAN) return
        val result = goAsync()
        val app = context.applicationContext as StreamLabApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (action == ACTION_SEED) seed(app) else clean(app)
            } catch (failure: Throwable) {
                Log.e(TAG, "ERROR ${failure.javaClass.simpleName}")
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun seed(app: StreamLabApplication) {
        clean(app)
        val resolver = app.contentResolver
        val uri = requireNotNull(resolver.insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, AUDIO_FILE_NAME)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/StreamLabQA")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            },
        )) { "Cannot create audio fixture" }

        try {
            resolver.openOutputStream(uri, "w")?.use(::writeWavTone)
                ?: error("Cannot write audio fixture")
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null) == 1) { "Cannot publish audio fixture" }

            val profileId = app.repository.dao.saveProfile(
                ProfileEntity(name = PROFILE_NAME, type = ProfileType.MANUAL.name),
            )
            app.repository.dao.saveSettings(ProfileSettingsEntity(profileId))
            app.repository.dao.upsertChannels(listOf(ChannelEntity(
                profileId = profileId,
                identity = "aaos-audio-focus-fixture",
                name = "AAOS audio focus test",
                category = "Audio test",
                tvgId = null,
                tvgName = null,
                logo = null,
                encryptedUrl = app.secrets.encrypt(uri.toString()),
                encryptedHeaders = app.secrets.encrypt("{}"),
            )))
            val channelId = app.repository.dao.channelsForProfile(profileId).single().id
            app.repository.markPlayed(channelId)
            Log.i(TAG, "READY uid=${Process.myUid()} channel=channel:$channelId")
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    private suspend fun clean(app: StreamLabApplication) = withContext(Dispatchers.IO) {
        app.repository.profiles.first()
            .filter { it.name == PROFILE_NAME }
            .forEach { app.repository.deleteProfile(it.id) }

        val resolver = app.contentResolver
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
            arrayOf(AUDIO_FILE_NAME),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                resolver.delete(
                    android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    null,
                    null,
                )
            }
        }
        Log.i(TAG, "CLEAN uid=${Process.myUid()}")
    }

    private fun writeWavTone(output: java.io.OutputStream) {
        val sampleRate = 16_000
        val sampleCount = sampleRate * 30
        val dataBytes = sampleCount * 2
        fun writeAscii(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
        fun writeLe16(value: Int) {
            output.write(value and 0xff)
            output.write((value ushr 8) and 0xff)
        }
        fun writeLe32(value: Int) {
            repeat(4) { index -> output.write((value ushr (8 * index)) and 0xff) }
        }

        writeAscii("RIFF")
        writeLe32(36 + dataBytes)
        writeAscii("WAVEfmt ")
        writeLe32(16)
        writeLe16(1)
        writeLe16(1)
        writeLe32(sampleRate)
        writeLe32(sampleRate * 2)
        writeLe16(2)
        writeLe16(16)
        writeAscii("data")
        writeLe32(dataBytes)
        repeat(sampleCount) { index ->
            val sample = (sin(2.0 * PI * 440.0 * index / sampleRate) * 2_000).toInt()
            writeLe16(sample)
        }
    }

    companion object {
        const val ACTION_SEED = "app.cleanroom.streamlab.qa.SEED_AUDIO"
        const val ACTION_CLEAN = "app.cleanroom.streamlab.qa.CLEAN_AUDIO"
        private const val TAG = "StreamLabAaosFocusFixture"
        private const val PROFILE_NAME = "StreamLab AAOS Focus QA"
        private const val AUDIO_FILE_NAME = "streamlab-aaos-focus-qa.wav"
    }
}
