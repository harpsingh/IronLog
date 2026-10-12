package com.ironlog.data.repository

import androidx.room.withTransaction
import com.ironlog.data.backup.BackupExercise
import com.ironlog.data.backup.BackupRoutine
import com.ironlog.data.backup.BackupRoutineExercise
import com.ironlog.data.backup.BackupSet
import com.ironlog.data.backup.BackupWorkout
import com.ironlog.data.backup.IronLogBackup
import com.ironlog.data.local.IronLogDatabase
import com.ironlog.data.local.dao.RoutineWithExercises
import com.ironlog.data.local.entity.Exercise
import com.ironlog.data.local.entity.Routine
import com.ironlog.data.local.entity.RoutineExercise
import com.ironlog.data.local.entity.SetLog
import com.ironlog.data.local.entity.WorkoutLog
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class IronLogRepository(private val db: IronLogDatabase) {
    private val exerciseDao = db.exerciseDao()
    private val routineDao = db.routineDao()
    private val workoutDao = db.workoutDao()

    // ---- Exercises ----
    val exercises: Flow<List<Exercise>> = exerciseDao.observeAll()

    suspend fun exercise(id: String): Exercise? = exerciseDao.getById(id)

    suspend fun saveCustomExercise(exercise: Exercise) {
        val id = exercise.id.ifBlank { "custom_" + UUID.randomUUID() }
        exerciseDao.upsert(exercise.copy(id = id, isCustom = true))
    }

    suspend fun deleteCustomExercise(id: String) = db.withTransaction {
        routineDao.deleteExercisesByExercise(id)
        exerciseDao.deleteCustom(id)
    }

    // ---- Routines ----
    val routines: Flow<List<RoutineWithExercises>> = routineDao.observeAll()

    suspend fun routine(id: Long): RoutineWithExercises? = routineDao.getWithExercises(id)

    /** Inserts or updates [routine] and replaces its exercise list. Returns the routine id. */
    suspend fun saveRoutine(routine: Routine, items: List<RoutineExercise>): Long = db.withTransaction {
        val id = if (routine.id == 0L) {
            routineDao.insert(routine.copy(sortOrder = Int.MAX_VALUE / 2))
        } else {
            routineDao.update(routine)
            routine.id
        }
        routineDao.deleteExercisesFor(id)
        routineDao.insertExercises(items.mapIndexed { i, it -> it.copy(id = 0, routineId = id, position = i) })
        id
    }

    suspend fun deleteRoutine(id: Long) = routineDao.delete(id)

    // ---- Workouts ----
    val finishedWorkouts: Flow<List<WorkoutLog>> = workoutDao.observeFinished()
    val completedSets: Flow<List<SetLog>> = workoutDao.observeCompletedSets()

    fun setsFor(workoutId: Long): Flow<List<SetLog>> = workoutDao.observeSets(workoutId)

    suspend fun workout(id: Long): WorkoutLog? = workoutDao.getWorkout(id)

    suspend fun previousSets(exerciseId: String): List<SetLog> = workoutDao.lastSessionSets(exerciseId)

    suspend fun saveWorkout(log: WorkoutLog, sets: List<SetLog>): Long = db.withTransaction {
        val id = workoutDao.insertWorkout(log)
        workoutDao.insertSets(sets.map { it.copy(id = 0, workoutId = id) })
        id
    }

    suspend fun deleteWorkout(id: Long) = workoutDao.deleteWorkout(id)

    // ---- Backup / restore ----
    suspend fun buildBackup(): IronLogBackup {
        val custom = exerciseDao.getCustom().map {
            BackupExercise(
                it.id, it.name, it.category, it.level, it.equipment, it.mechanic, it.force,
                it.primaryMuscles, it.secondaryMuscles, it.instructions,
            )
        }
        val routines = routineDao.getAllWithExercises().map { r ->
            BackupRoutine(
                name = r.routine.name,
                description = r.routine.description,
                isDefault = r.routine.isDefault,
                sortOrder = r.routine.sortOrder,
                createdAt = r.routine.createdAt,
                exercises = r.exercises.sortedBy { it.position }.map {
                    BackupRoutineExercise(it.exerciseId, it.position, it.sets, it.repMin, it.repMax, it.restSeconds)
                },
            )
        }
        val routineNames = routineDao.getAllWithExercises().associate { it.routine.id to it.routine.name }
        val workouts = workoutDao.getAllFinishedWithSets().map { w ->
            BackupWorkout(
                name = w.workout.name,
                routineName = w.workout.routineId?.let { routineNames[it] },
                startedAt = w.workout.startedAt,
                finishedAt = w.workout.finishedAt ?: w.workout.startedAt,
                notes = w.workout.notes,
                sets = w.sets.filter { it.completed }.map {
                    BackupSet(it.exerciseId, it.exerciseName, it.exerciseOrder, it.setIndex, it.weight, it.reps, it.completedAt)
                },
            )
        }
        return IronLogBackup(customExercises = custom, routines = routines, workouts = workouts)
    }

    /** Replaces all user data (custom exercises, routines, history) with the backup contents. */
    suspend fun restoreBackup(backup: IronLogBackup) = db.withTransaction {
        exerciseDao.deleteAllCustom()
        routineDao.deleteAll()
        workoutDao.deleteAllWorkouts()

        exerciseDao.upsertAll(backup.customExercises.map {
            Exercise(
                it.id, it.name, it.category, it.level, it.equipment, it.mechanic, it.force,
                it.primaryMuscles, it.secondaryMuscles, it.instructions, imagePath = null, isCustom = true,
            )
        })

        val routineIds = HashMap<String, Long>()
        backup.routines.forEachIndexed { index, r ->
            val id = routineDao.insert(
                Routine(
                    name = r.name, description = r.description, isDefault = r.isDefault,
                    sortOrder = index, createdAt = r.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                )
            )
            routineIds.putIfAbsent(r.name, id)
            routineDao.insertExercises(r.exercises.sortedBy { it.position }.mapIndexed { i, e ->
                RoutineExercise(
                    routineId = id, exerciseId = e.exerciseId, position = i,
                    sets = e.sets, repMin = e.repMin, repMax = e.repMax, restSeconds = e.restSeconds,
                )
            })
        }

        backup.workouts.forEach { w ->
            val id = workoutDao.insertWorkout(
                WorkoutLog(
                    routineId = w.routineName?.let { routineIds[it] },
                    name = w.name, startedAt = w.startedAt, finishedAt = w.finishedAt, notes = w.notes,
                )
            )
            workoutDao.insertSets(w.sets.map {
                SetLog(
                    workoutId = id, exerciseId = it.exerciseId, exerciseName = it.exerciseName,
                    exerciseOrder = it.exerciseOrder, setIndex = it.setIndex, weight = it.weight,
                    reps = it.reps, completed = true, completedAt = it.completedAt,
                )
            })
        }
    }
}
