package com.ironlog.domain.stats

import com.ironlog.data.local.entity.SetLog
import com.ironlog.data.local.entity.WorkoutLog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class ChartPoint(val label: String, val value: Float)

/** Pure functions that turn raw workout history into chart-ready series. */
object StatsCalculator {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun volumeOf(sets: List<SetLog>): Double = sets.filter { it.completed }.sumOf { it.weight * it.reps }

    /** Total weight lifted (kg·reps) for each finished workout, oldest first. */
    fun volumePerWorkout(workouts: List<WorkoutLog>, sets: List<SetLog>): List<ChartPoint> {
        val byWorkout = sets.groupBy { it.workoutId }
        return workouts.sortedBy { it.startedAt }.map { w ->
            val d = toDate(w.startedAt)
            ChartPoint("${d.monthValue}/${d.dayOfMonth}", volumeOf(byWorkout[w.id].orEmpty()).toFloat())
        }
    }

    /** Number of workouts in each of the last [weeks] weeks (Mon–Sun), oldest first. */
    fun workoutsPerWeek(workouts: List<WorkoutLog>, weeks: Int = 8, today: LocalDate = LocalDate.now()): List<ChartPoint> {
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val counts = workouts.groupingBy { toDate(it.startedAt).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.eachCount()
        return (weeks - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back.toLong())
            ChartPoint("${start.monthValue}/${start.dayOfMonth}", (counts[start] ?: 0).toFloat())
        }
    }

    /** Best estimated one-rep max (Epley) per session for one exercise, oldest first. */
    fun strengthProgress(workouts: List<WorkoutLog>, sets: List<SetLog>, exerciseId: String): List<ChartPoint> {
        val byId = workouts.associateBy { it.id }
        return sets.filter { it.exerciseId == exerciseId && it.completed && it.weight > 0 }
            .groupBy { it.workoutId }
            .mapNotNull { (id, list) -> byId[id]?.let { it to list } }
            .sortedBy { it.first.startedAt }
            .map { (w, list) ->
                val best = list.maxOf { it.weight * (1 + it.reps / 30.0) }
                val d = toDate(w.startedAt)
                ChartPoint("${d.monthValue}/${d.dayOfMonth}", best.toFloat())
            }
    }

    /** Current streak of consecutive weeks containing at least one workout. */
    fun weekStreak(workouts: List<WorkoutLog>, today: LocalDate = LocalDate.now()): Int {
        val weeks = workouts.map { toDate(it.startedAt).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSet()
        var cursor = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (cursor !in weeks) cursor = cursor.minusWeeks(1)
        var streak = 0
        while (cursor in weeks) { streak++; cursor = cursor.minusWeeks(1) }
        return streak
    }
}
