package app.cleanroom.streamlab.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceFailoverTest {
    @Test fun storesAtMostTwoBackups() {
        val stored = SourceFailover.encode(listOf("https://a", "https://b", "https://c", "https://a"))
        assertEquals(listOf("https://a", "https://b"), SourceFailover.decode(stored))
    }

    @Test fun emptyStorageMeansNoBackup() {
        assertEquals(emptyList<String>(), SourceFailover.decode(""))
        assertEquals(emptyList<String>(), SourceFailover.decode(null))
    }

    @Test fun legacySingleBackupStillReads() {
        assertEquals(listOf("https://old"), SourceFailover.decode("https://old"))
    }

    @Test fun neverMoreThanThreeSourcesInAll() {
        assertEquals(listOf("m", "b1", "b2"), SourceFailover.ordered("m", listOf("b1", "m", "b2", "b3")))
        assertEquals(1, SourceFailover.nextIndex(0, 3))
        assertEquals(2, SourceFailover.nextIndex(1, 3))
        assertNull(SourceFailover.nextIndex(2, 3))
        assertNull(SourceFailover.nextIndex(0, 1))
        assertNull(SourceFailover.nextIndex(2, 9))
    }

    @Test fun qualityVariantsAreTheSameChannel() {
        val key = SourceFailover.sameChannelKey("TF1", null)
        assertEquals(key, SourceFailover.sameChannelKey("TF1 HD", null))
        assertEquals(key, SourceFailover.sameChannelKey("TF1 (backup)", null))
        assertEquals(key, SourceFailover.sameChannelKey("tf1 [1080p]", ""))
        assertEquals(SourceFailover.sameChannelKey("x", "TF1.fr"), SourceFailover.sameChannelKey("y", "tf1.fr"))
    }

    @Test fun backupsComeFromTheSameChannelOnly() {
        val entries = listOf("k1" to "u1", "k2" to "u2", "k1" to "u3", "k1" to "u4", "k1" to "u5")
        val backups = SourceFailover.backupsByIndex(entries)
        assertEquals(listOf("u3", "u4"), backups[0])
        assertEquals(emptyList<String>(), backups[1])
        assertEquals(listOf("u1", "u4"), backups[2])
    }
}
