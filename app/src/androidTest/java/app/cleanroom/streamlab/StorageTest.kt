package app.cleanroom.streamlab

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cleanroom.streamlab.core.Network
import app.cleanroom.streamlab.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageTest {
    @Test fun keystoreRoundTripAndUniqueCiphertexts() {
        val vault = Secrets()
        val a = vault.encrypt("private-token")
        val b = vault.encrypt("private-token")
        assertNotEquals(a, b)
        assertEquals("private-token", vault.decrypt(a))
        assertEquals("private-token", vault.decrypt(b))
    }
    @Test fun favoriteAndCascade() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, StreamLabDatabase::class.java).build()
        try {
            val dao = db.catalog()
            val profile = dao.saveProfile(ProfileEntity(name = "Test", type = ProfileType.MANUAL.name))
            dao.upsertChannels(listOf(ChannelEntity(profileId = profile, identity = "one", name = "One", category = "Test", tvgId = null, tvgName = null, logo = null, encryptedUrl = "cipher")))
            val channel = dao.channelsForProfile(profile).single()
            dao.addFavorite(FavoriteEntity(channel.id))
            assertTrue(dao.channels().first().single().favorite)
            dao.deleteProfile(profile)
            assertTrue(dao.channels().first().isEmpty())
        } finally { db.close() }
    }
    @Test fun profileUpdatePreservesChannelsFavoritesAndRecent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, StreamLabDatabase::class.java).build()
        try {
            val dao = db.catalog()
            val profileId = dao.saveProfile(ProfileEntity(name = "Before", type = ProfileType.MANUAL.name))
            dao.upsertChannels(listOf(ChannelEntity(profileId = profileId, identity = "stable", name = "Stable", category = "QA", tvgId = null, tvgName = null, logo = null, encryptedUrl = "cipher")))
            val channel = dao.channelsForProfile(profileId).single()
            dao.addFavorite(FavoriteEntity(channel.id))
            dao.markRecent(RecentEntity(channel.id, 1234L))

            dao.saveProfile((dao.profile(profileId)!!).copy(name = "After"))

            val preserved = dao.channelsForProfile(profileId).single()
            assertEquals(channel.id, preserved.id)
            assertEquals("After", dao.profile(profileId)!!.name)
            val row = dao.channels().first().single { it.id == channel.id }
            assertTrue(row.favorite)
            assertEquals(1234L, row.playedAt)
        } finally { db.close() }
    }
    @Test fun playbackProgressResumesOnlyMeaningfulPosition() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, StreamLabDatabase::class.java).build()
        try {
            val repo = CatalogRepository(context, db, Secrets(), Network())
            val dao = db.catalog()
            val profileId = dao.saveProfile(ProfileEntity(name = "Resume", type = ProfileType.MANUAL.name))
            dao.upsertChannels(listOf(ChannelEntity(profileId = profileId, identity = "resume", name = "Resume movie", category = "QA", tvgId = null, tvgName = null, logo = null, encryptedUrl = "cipher")))
            val channelId = dao.channelsForProfile(profileId).single().id
            repo.markPlayed(channelId)

            repo.updatePlaybackProgress(channelId, 42_000L, 120_000L)
            assertEquals(42_000L, repo.resumePositionMs(channelId))

            repo.updatePlaybackProgress(channelId, 5_000L, 120_000L)
            assertEquals(0L, repo.resumePositionMs(channelId))

            repo.updatePlaybackProgress(channelId, 115_000L, 120_000L)
            assertEquals(0L, repo.resumePositionMs(channelId))
        } finally { db.close() }
    }

    @Test fun portableRepositoryExportOmitsSecrets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, StreamLabDatabase::class.java).build()
        try {
            val repo = CatalogRepository(context, db, Secrets(), Network())
            val id = repo.createProfile("Portable", ProfileType.M3U_URL,
                "https://example.com/list.m3u?token=private-token", "private-user", "private-password")
            val exported = repo.exportPortable(repo.profiles.first())
            assertTrue(exported.contains("Portable"))
            assertFalse(exported.contains("private-token"))
            assertFalse(exported.contains("private-user"))
            assertFalse(exported.contains("private-password"))
            assertFalse(exported.contains("example.com"))
            assertEquals(1, repo.importPortable(exported))
            val restored = repo.profiles.first().single { it.id != id }
            assertEquals("", restored.encryptedSource)
            assertEquals("", restored.encryptedCredentials)
        } finally { db.close() }
    }

    @Test fun portableImportRejectsUnsupportedStalkerBeforeSavingAnyProfile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, StreamLabDatabase::class.java).build()
        try {
            val repo = CatalogRepository(context, db, Secrets(), Network())
            val portable = """{"format":"streamlab-portable-v1","profiles":[{"name":"Supported","type":"M3U_URL"},{"name":"Legacy Stalker","type":"STALKER"}]}"""
            val failure = runCatching { repo.importPortable(portable) }.exceptionOrNull()
            assertTrue("Unsupported Stalker profile should be rejected", failure is IllegalArgumentException)
            assertTrue("Import must be atomic when a profile type is unsupported", repo.profiles.first().isEmpty())
        } finally { db.close() }
    }
}
