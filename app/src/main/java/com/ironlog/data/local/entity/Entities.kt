package com.ironlog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val level: String,
    val equipment: String,
    val mechanic: String?,
    val force: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    /** Path relative to the `assets/` directory (bundled thumbnail), or null. */
    val imagePath: String?,
    val isCustom: Boolean = false,
)

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [ForeignKey(
        entity = Routine::class,
        parentColumns = ["id"],
        childColumns = ["routineId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("routineId")],
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: String,
    val position: Int,
    val sets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val restSeconds: Int = 90,
)

@Entity(tableName = "workout_logs")
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long?,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val notes: String = "",
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [ForeignKey(
        entity = WorkoutLog::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    /** Snapshot so history survives exercise deletion. */
    val exerciseName: String,
    val exerciseOrder: Int,
    val setIndex: Int,
    val weight: Double,
    val reps: Int,
    val completed: Boolean,
    val completedAt: Long?,
)
