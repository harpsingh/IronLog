package com.ironlog

import com.ironlog.data.backup.BackupSet
import com.ironlog.data.backup.BackupWorkout
import com.ironlog.data.backup.InvalidBackupException
import com.ironlog.data.backup.IronLogBackup
import com.ironlog.data.backup.IronLogBackupCodec
import com.ironlog.data.local.entity.SetLog
import com.ironlog.domain.stats.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupAndStatsTest {
    @Test
    fun backupRoundTrips() {
        val backup = IronLogBackup(
            workouts = listOf(
                BackupWorkout("Push", null, 1L, 2L, sets = listOf(BackupSet("e1", "Bench", 0, 0, 60.0, 10))),
            ),
        )
        val decoded = IronLogBackupCodec.decode(IronLogBackupCodec.encode(backup))
        assertEquals(backup, decoded)
    }

    @Test
    fun rejectsGarbageAndForeignFiles() {
        assertThrows(InvalidBackupException::class.java) { IronLogBackupCodec.decode("not json") }
        assertThrows(InvalidBackupException::class.java) { IronLogBackupCodec.decode("""{"app":"other"}""") }
        assertThrows(InvalidBackupException::class.java) { IronLogBackupCodec.decode("""{"app":"ironlog","version":99}""") }
    }

    @Test
    fun volumeIgnoresIncompleteSets() {
        val sets = listOf(
            SetLog(1, 1, "e", "E", 0, 0, 50.0, 10, true, null),
            SetLog(2, 1, "e", "E", 0, 1, 50.0, 10, false, null),
        )
        assertEquals(500.0, StatsCalculator.volumeOf(sets), 0.001)
    }
}
