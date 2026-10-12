package com.ironlog.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Root document written to / read from `ironlog_backup.json`. */
@Serializable
data class IronLogBackup(
    val app: String = APP_ID,
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val customExercises: List<BackupExercise> = emptyList(),
    val routines: List<BackupRoutine> = emptyList(),
    val workouts: List<BackupWorkout> = emptyList(),
) {
    companion object {
        const val APP_ID = "ironlog"
        const val CURRENT_VERSION = 1
        const val DEFAULT_FILE_NAME = "ironlog_backup.json"
    }
}

@Serializable
data class BackupExercise(
    val id: String,
    val name: String,
    val category: String = "strength",
    val level: String = "beginner",
    val equipment: String = "none",
    val mechanic: String? = null,
    val force: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
)

@Serializable
data class BackupRoutine(
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    val exercises: List<BackupRoutineExercise> = emptyList(),
)

@Serializable
data class BackupRoutineExercise(
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

@Serializable
data class BackupWorkout(
    val name: String,
    val routineName: String? = null,
    val startedAt: Long,
    val finishedAt: Long,
    val notes: String = "",
    val sets: List<BackupSet> = emptyList(),
)

@Serializable
data class BackupSet(
    val exerciseId: String,
    val exerciseName: String,
    val exerciseOrder: Int,
    val setIndex: Int,
    val weight: Double,
    val reps: Int,
    val completedAt: Long? = null,
)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** JSON encode/decode + validation for [IronLogBackup]. */
object IronLogBackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: IronLogBackup): String = json.encodeToString(backup)

    fun decode(text: String): IronLogBackup {
        val backup = try {
            json.decodeFromString<IronLogBackup>(text)
        } catch (e: SerializationException) {
            throw InvalidBackupException("This file is not a valid IronLog backup.", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This file is not a valid IronLog backup.", e)
        }
        if (backup.app != IronLogBackup.APP_ID) throw InvalidBackupException("Not an IronLog backup file.")
        if (backup.version > IronLogBackup.CURRENT_VERSION) {
            throw InvalidBackupException("Backup was created by a newer version of IronLog.")
        }
        return backup
    }
}
