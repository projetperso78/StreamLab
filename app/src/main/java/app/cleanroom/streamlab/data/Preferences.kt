package app.cleanroom.streamlab.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")
data class AppPreferences(val theme: String = "dark", val dense: Boolean = false, val previews: Boolean = false, val timeoutSeconds: Int = 25, val preferHardware: Boolean = true)
class Preferences(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val dense = booleanPreferencesKey("dense")
    private val previews = booleanPreferencesKey("previews")
    private val timeout = intPreferencesKey("timeout")
    private val hardware = booleanPreferencesKey("hardware")
    private val lastPlaybackMediaId = stringPreferencesKey("last_playback_media_id")
    val values = context.store.data.map { AppPreferences(it[theme] ?: "dark", it[dense] ?: false, it[previews] ?: false, it[timeout] ?: 25, it[hardware] ?: true) }
    suspend fun setTheme(value: String) = context.store.edit { it[theme] = value }
    suspend fun setDense(value: Boolean) = context.store.edit { it[dense] = value }
    suspend fun setPreviews(value: Boolean) = context.store.edit { it[previews] = value }
    suspend fun setTimeout(value: Int) = context.store.edit { it[timeout] = value.coerceIn(5, 120) }
    suspend fun setHardware(value: Boolean) = context.store.edit { it[hardware] = value }
    suspend fun setLastPlaybackMediaId(value: String?) = context.store.edit { prefs ->
        if (value.isNullOrBlank()) prefs.remove(lastPlaybackMediaId) else prefs[lastPlaybackMediaId] = value
    }
    suspend fun lastPlaybackMediaId(): String? = context.store.data.first()[lastPlaybackMediaId]
}
