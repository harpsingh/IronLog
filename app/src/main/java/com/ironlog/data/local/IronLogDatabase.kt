package com.ironlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ironlog.data.local.dao.ExerciseDao
import com.ironlog.data.local.dao.RoutineDao
import com.ironlog.data.local.dao.WorkoutDao
import com.ironlog.data.local.entity.Exercise
import com.ironlog.data.local.entity.Routine
import com.ironlog.data.local.entity.RoutineExercise
import com.ironlog.data.local.entity.SetLog
import com.ironlog.data.local.entity.WorkoutLog

@Database(
    entities = [Exercise::class, Routine::class, RoutineExercise::class, WorkoutLog::class, SetLog::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IronLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        fun build(context: Context): IronLogDatabase =
            Room.databaseBuilder(context.applicationContext, IronLogDatabase::class.java, "ironlog.db")
                .addCallback(SeedCallback(context.applicationContext))
                .build()
    }
}
