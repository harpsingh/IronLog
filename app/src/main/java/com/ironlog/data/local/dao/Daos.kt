package com.ironlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.ironlog.data.local.entity.Exercise
import com.ironlog.data.local.entity.Routine
import com.ironlog.data.local.entity.RoutineExercise
import com.ironlog.data.local.entity.SetLog
import com.ironlog.data.local.entity.WorkoutLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): Exercise?

    @Query("SELECT * FROM exercises WHERE isCustom = 1")
    suspend fun getCustom(): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(exercises: List<Exercise>)

    @Query("DELETE FROM exercises WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: String)

    @Query("DELETE FROM exercises WHERE isCustom = 1")
    suspend fun deleteAllCustom()
}

data class RoutineWithExercises(
    @Embedded val routine: Routine,
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val exercises: List<RoutineExercise>,
)

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getWithExercises(id: Long): RoutineWithExercises?

    @Transaction
    @Query("SELECT * FROM routines ORDER BY sortOrder, id")
    suspend fun getAllWithExercises(): List<RoutineWithExercises>

    @Insert
    suspend fun insert(routine: Routine): Long

    @Update
    suspend fun update(routine: Routine)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()

    @Insert
    suspend fun insertExercises(items: List<RoutineExercise>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteExercisesFor(routineId: Long)

    @Query("DELETE FROM routine_exercises WHERE exerciseId = :exerciseId")
    suspend fun deleteExercisesByExercise(exerciseId: String)
}

data class WorkoutWithSets(
    @Embedded val workout: WorkoutLog,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val sets: List<SetLog>,
)

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(log: WorkoutLog): Long

    @Insert
    suspend fun insertSets(sets: List<SetLog>)

    @Query("SELECT * FROM workout_logs WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<WorkoutLog>>

    @Query("SELECT * FROM set_logs WHERE completed = 1")
    fun observeCompletedSets(): Flow<List<SetLog>>

    @Query("SELECT * FROM workout_logs WHERE id = :id")
    suspend fun getWorkout(id: Long): WorkoutLog?

    @Query("SELECT * FROM set_logs WHERE workoutId = :workoutId ORDER BY exerciseOrder, setIndex")
    fun observeSets(workoutId: Long): Flow<List<SetLog>>

    @Query("DELETE FROM workout_logs WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("DELETE FROM workout_logs")
    suspend fun deleteAllWorkouts()

    @Transaction
    @Query("SELECT * FROM workout_logs WHERE finishedAt IS NOT NULL ORDER BY startedAt")
    suspend fun getAllFinishedWithSets(): List<WorkoutWithSets>

    /** Completed sets of the most recent finished session that included [exerciseId]. */
    @Query(
        """
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId AND completed = 1 AND workoutId = (
            SELECT w.id FROM workout_logs w
            JOIN set_logs s ON s.workoutId = w.id
            WHERE s.exerciseId = :exerciseId AND s.completed = 1 AND w.finishedAt IS NOT NULL
            ORDER BY w.finishedAt DESC LIMIT 1
        )
        ORDER BY setIndex
        """
    )
    suspend fun lastSessionSets(exerciseId: String): List<SetLog>
}
