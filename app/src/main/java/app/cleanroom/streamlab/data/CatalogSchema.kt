package app.cleanroom.streamlab.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class ProfileType { M3U_URL, LOCAL_M3U, LOCAL_MEDIA, XTREAM, MANUAL, STALKER, WEBDAV }

@Entity(tableName = "catalog_profiles")
data class ProfileEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val type: String,
    val encryptedSource: String = "", val encryptedCredentials: String = "", val encryptedEpg: String = "",
    val archived: Boolean = false, val lastRefreshed: Long = 0)

@Entity(tableName = "catalog_channels", foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)], indices = [Index("profileId"), Index(value = ["profileId", "identity"], unique = true)])
data class ChannelEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val profileId: Long, val identity: String,
    val name: String, val category: String, val tvgId: String?, val tvgName: String?, val logo: String?,
    val encryptedUrl: String, val encryptedHeaders: String = "", val encryptedFallbackUrl: String = "",
    val country: String? = null, val language: String? = null)

@Entity(tableName = "catalog_favorites", primaryKeys = ["channelId"], foreignKeys = [ForeignKey(entity = ChannelEntity::class, parentColumns = ["id"], childColumns = ["channelId"], onDelete = ForeignKey.CASCADE)])
data class FavoriteEntity(val channelId: Long)

@Entity(tableName = "catalog_recent", primaryKeys = ["channelId"], foreignKeys = [ForeignKey(entity = ChannelEntity::class, parentColumns = ["id"], childColumns = ["channelId"], onDelete = ForeignKey.CASCADE)])
data class RecentEntity(
    val channelId: Long,
    val playedAt: Long,
    @ColumnInfo(defaultValue = "0") val positionMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0L,
)

@Entity(tableName = "catalog_programmes", indices = [Index("channelId"), Index("startMs")], foreignKeys = [ForeignKey(entity = ChannelEntity::class, parentColumns = ["id"], childColumns = ["channelId"], onDelete = ForeignKey.CASCADE)])
data class ProgrammeEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val channelId: Long, val title: String, val startMs: Long, val endMs: Long)

@Entity(tableName = "catalog_profile_settings", primaryKeys = ["profileId"], foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)])
data class ProfileSettingsEntity(val profileId: Long, val refreshHours: Int = 0, val epgRefreshHours: Int = 0, val playbackTimeoutSeconds: Int = 25)

data class CatalogChannelRow(val id: Long, val profileId: Long, val name: String, val category: String, val tvgId: String?, val tvgName: String?, val logo: String?, val favorite: Boolean, val playedAt: Long?,
    val country: String? = null, val language: String? = null)

@Dao interface CatalogDao {
    @Query("SELECT * FROM catalog_profiles WHERE archived = 0 ORDER BY name") fun profiles(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM catalog_profiles WHERE id = :id LIMIT 1") suspend fun profile(id: Long): ProfileEntity?
    @Query("SELECT * FROM catalog_profiles WHERE name = :name AND archived = 0 LIMIT 1") suspend fun profileByName(name: String): ProfileEntity?
    @Upsert suspend fun saveProfile(profile: ProfileEntity): Long
    @Query("DELETE FROM catalog_profiles WHERE id = :id") suspend fun deleteProfile(id: Long)
    @Query("UPDATE catalog_profiles SET lastRefreshed = :time WHERE id = :id") suspend fun markRefreshed(id: Long, time: Long)
    @Upsert suspend fun upsertChannels(channels: List<ChannelEntity>)
    @Query("DELETE FROM catalog_channels WHERE id IN (:ids)") suspend fun deleteChannelsByIds(ids: List<Long>)
    @Query("SELECT c.id, c.profileId, c.name, c.category, c.tvgId, c.tvgName, c.logo, (f.channelId IS NOT NULL) AS favorite, r.playedAt, c.country, c.language FROM catalog_channels c LEFT JOIN catalog_favorites f ON f.channelId = c.id LEFT JOIN catalog_recent r ON r.channelId = c.id ORDER BY c.name") fun channels(): Flow<List<CatalogChannelRow>>
    @Query("SELECT * FROM catalog_channels WHERE id = :id LIMIT 1") suspend fun channel(id: Long): ChannelEntity?
    @Query("SELECT * FROM catalog_channels WHERE country IS NULL AND language IS NULL") suspend fun channelsWithoutOrigin(): List<ChannelEntity>
    @Query("SELECT * FROM catalog_channels") suspend fun channelsForAllProfiles(): List<ChannelEntity>
    @Query("UPDATE catalog_channels SET name = :name WHERE id = :id") suspend fun setName(id: Long, name: String)
    @Query("UPDATE catalog_channels SET country = :country, language = :language WHERE id = :id") suspend fun setOrigin(id: Long, country: String?, language: String?)
    @Query("UPDATE catalog_channels SET encryptedFallbackUrl = :cipher WHERE id = :id") suspend fun setFallback(id: Long, cipher: String)
    @Query("SELECT * FROM catalog_channels WHERE profileId = :profileId") suspend fun channelsForProfile(profileId: Long): List<ChannelEntity>
    @Query("SELECT * FROM catalog_favorites WHERE channelId = :channelId LIMIT 1") suspend fun favorite(channelId: Long): FavoriteEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(favorite: FavoriteEntity)
    @Query("DELETE FROM catalog_favorites WHERE channelId = :channelId") suspend fun removeFavorite(channelId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun markRecent(recent: RecentEntity)
    @Query("SELECT * FROM catalog_recent WHERE channelId = :channelId LIMIT 1") suspend fun recent(channelId: Long): RecentEntity?
    @Query("UPDATE catalog_recent SET positionMs = :positionMs, durationMs = :durationMs WHERE channelId = :channelId")
    suspend fun updateRecentProgress(channelId: Long, positionMs: Long, durationMs: Long)
    @Query("SELECT channelId FROM catalog_recent ORDER BY playedAt DESC LIMIT 1") suspend fun latestPlayedChannelId(): Long?
    @Query("SELECT * FROM catalog_recent WHERE durationMs > 0 AND positionMs >= 10000 ORDER BY playedAt DESC LIMIT 20") fun inProgress(): Flow<List<RecentEntity>>
    @Query("DELETE FROM catalog_programmes WHERE channelId IN (SELECT id FROM catalog_channels WHERE profileId = :profileId)") suspend fun deleteProgrammes(profileId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProgrammes(programmes: List<ProgrammeEntity>)
    @Query("SELECT * FROM catalog_programmes WHERE channelId = :channelId AND endMs > :now ORDER BY startMs LIMIT 3") suspend fun upcoming(channelId: Long, now: Long): List<ProgrammeEntity>
    @Query("SELECT p.* FROM catalog_programmes p JOIN catalog_channels c ON c.id = p.channelId WHERE c.profileId = :profileId AND p.endMs >= :after ORDER BY p.startMs LIMIT 2000") fun guide(profileId: Long, after: Long): Flow<List<ProgrammeEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(settings: ProfileSettingsEntity)
    @Query("SELECT * FROM catalog_profile_settings WHERE profileId = :profileId LIMIT 1") suspend fun settings(profileId: Long): ProfileSettingsEntity?
}

@Database(
    entities = [ProfileEntity::class, ChannelEntity::class, FavoriteEntity::class, RecentEntity::class, ProgrammeEntity::class, ProfileSettingsEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true,
)
abstract class StreamLabDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    companion object { fun open(context: Context): StreamLabDatabase = Room.databaseBuilder(context, StreamLabDatabase::class.java, "streamlab-catalog.db").build() }
}
