package com.ironlog.data.local

import android.content.ContentValues
import android.content.Context
import androidx.room.OnConflictStrategy
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON shape of the bundled `assets/exercises.json` (derived from yuhonas/free-exercise-db). */
@Serializable
data class SeedExercise(
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
    val image: String? = null,
)

/**
 * Populates the database on first creation. Uses the raw [SupportSQLiteDatabase] handed to
 * `onCreate` (already inside Room's creation transaction), so the data is guaranteed to be
 * present before the first query runs and there is no race with the UI.
 */
class SeedCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedExercises(db)
        seedDefaultRoutines(db)
    }

    private fun seedExercises(db: SupportSQLiteDatabase) {
        val text = context.assets.open(EXERCISES_ASSET).bufferedReader().use { it.readText() }
        val seeds = Json { ignoreUnknownKeys = true }.decodeFromString<List<SeedExercise>>(text)
        for (e in seeds) {
            val v = ContentValues().apply {
                put("id", e.id)
                put("name", e.name)
                put("category", e.category)
                put("level", e.level)
                put("equipment", e.equipment)
                put("mechanic", e.mechanic)
                put("force", e.force)
                put("primaryMuscles", Converters.join(e.primaryMuscles))
                put("secondaryMuscles", Converters.join(e.secondaryMuscles))
                put("instructions", Converters.join(e.instructions))
                put("imagePath", e.image)
                put("isCustom", 0)
            }
            db.insert("exercises", OnConflictStrategy.REPLACE, v)
        }
    }

    private fun seedDefaultRoutines(db: SupportSQLiteDatabase) {
        for ((index, plan) in DefaultPlans.all.withIndex()) {
            val routineId = db.insert("routines", OnConflictStrategy.ABORT, ContentValues().apply {
                put("name", plan.name)
                put("description", plan.description)
                put("isDefault", 1)
                put("sortOrder", index)
                put("createdAt", System.currentTimeMillis())
            })
            plan.items.forEachIndexed { pos, item ->
                db.insert("routine_exercises", OnConflictStrategy.ABORT, ContentValues().apply {
                    put("routineId", routineId)
                    put("exerciseId", item.exerciseId)
                    put("position", pos)
                    put("sets", item.sets)
                    put("repMin", item.repMin)
                    put("repMax", item.repMax)
                    put("restSeconds", item.rest)
                })
            }
        }
    }

    companion object {
        const val EXERCISES_ASSET = "exercises.json"
    }
}

/** Pre-loaded 3-day full-body dumbbell programme (A / B / A, then B / A / B the next week). */
object DefaultPlans {
    data class Item(val exerciseId: String, val sets: Int, val repMin: Int, val repMax: Int, val rest: Int)
    data class Plan(val name: String, val description: String, val items: List<Item>)

    val all = listOf(
        Plan(
            name = "Full Body – Workout A",
            description = "3-day dumbbell programme. Alternate A / B / A one week, B / A / B the next.",
            items = listOf(
                Item("Dumbbell_Squat", 3, 8, 12, 90),
                Item("Dumbbell_Bench_Press", 3, 8, 12, 90),
                Item("One-Arm_Dumbbell_Row", 3, 8, 12, 90),
                Item("Dumbbell_Shoulder_Press", 3, 8, 12, 75),
                Item("Dumbbell_Bicep_Curl", 2, 10, 15, 60),
                Item("Tricep_Dumbbell_Kickback", 2, 10, 15, 60),
            ),
        ),
        Plan(
            name = "Full Body – Workout B",
            description = "3-day dumbbell programme. Alternate A / B / A one week, B / A / B the next.",
            items = listOf(
                Item("Stiff-Legged_Dumbbell_Deadlift", 3, 8, 12, 90),
                Item("Incline_Dumbbell_Press", 3, 8, 12, 90),
                Item("Bent_Over_Two-Dumbbell_Row", 3, 8, 12, 90),
                Item("Dumbbell_Lunges", 3, 10, 12, 75),
                Item("Side_Lateral_Raise", 3, 12, 15, 60),
                Item("Hammer_Curls", 2, 10, 15, 60),
            ),
        ),
    )
}
