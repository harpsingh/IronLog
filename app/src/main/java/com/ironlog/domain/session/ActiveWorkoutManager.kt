package com.ironlog.domain.session

import com.ironlog.data.local.entity.Exercise
import com.ironlog.data.local.entity.SetLog
import com.ironlog.data.local.entity.WorkoutLog
import com.ironlog.data.repository.IronLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveSet(
    val weight: Double,
    val reps: Int,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val prevWeight: Double? = null,
    val prevReps: Int? = null,
)

data class ActiveExercise(
    val exercise: Exercise,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val sets: List<ActiveSet>,
)

data class RestState(val endsAt: Long, val totalSeconds: Int)

data class ActiveWorkoutState(
    val routineId: Long?,
    val name: String,
    val startedAt: Long,
    val exercises: List<ActiveExercise>,
    val rest: RestState? = null,
) {
    val completedSets: Int get() = exercises.sumOf { ex -> ex.sets.count { it.completed } }
    val totalSets: Int get() = exercises.sumOf { it.sets.size }
}

/**
 * Holds the in-progress workout so it survives screen navigation, drives the rest timer
 * and persists the session to Room when finished.
 */
class ActiveWorkoutManager(
    private val repository: IronLogRepository,
    private val notifier: RestTimerNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var restJob: Job? = null

    private val _state = MutableStateFlow<ActiveWorkoutState?>(null)
    val state: StateFlow<ActiveWorkoutState?> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value != null

    /** Starts a session from a saved routine, auto-filling values from the previous session. */
    suspend fun startFromRoutine(routineId: Long) {
        if (isActive) return
        val routine = repository.routine(routineId) ?: return
        val exercises = routine.exercises.sortedBy { it.position }.mapNotNull { item ->
            val exercise = repository.exercise(item.exerciseId) ?: return@mapNotNull null
            buildExercise(exercise, item.sets, item.repMin, item.repMax, item.restSeconds)
        }
        _state.value = ActiveWorkoutState(routineId, routine.routine.name, System.currentTimeMillis(), exercises)
    }

    fun startEmpty() {
        if (isActive) return
        _state.value = ActiveWorkoutState(null, "Quick Workout", System.currentTimeMillis(), emptyList())
    }

    suspend fun addExercise(exercise: Exercise) {
        val built = buildExercise(exercise, sets = 3, repMin = 8, repMax = 12, rest = 90)
        _state.update { it?.copy(exercises = it.exercises + built) }
    }

    fun removeExercise(index: Int) = mutate { s ->
        s.copy(exercises = s.exercises.filterIndexed { i, _ -> i != index })
    }

    fun updateSet(exIndex: Int, setIndex: Int, weight: Double? = null, reps: Int? = null) =
        updateSetInternal(exIndex, setIndex) { it.copy(weight = weight ?: it.weight, reps = reps ?: it.reps) }

    fun toggleSet(exIndex: Int, setIndex: Int) {
        val current = _state.value?.exercises?.getOrNull(exIndex)?.sets?.getOrNull(setIndex) ?: return
        val nowCompleted = !current.completed
        updateSetInternal(exIndex, setIndex) {
            it.copy(completed = nowCompleted, completedAt = if (nowCompleted) System.currentTimeMillis() else null)
        }
        if (nowCompleted) startRest(exIndex, setIndex) else skipRest()
    }

    fun addSet(exIndex: Int) = mutateExercise(exIndex) { ex ->
        val last = ex.sets.lastOrNull()
        ex.copy(sets = ex.sets + ActiveSet(last?.weight ?: 0.0, last?.reps ?: ex.repMin))
    }

    fun removeSet(exIndex: Int, setIndex: Int) = mutateExercise(exIndex) { ex ->
        ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != setIndex })
    }

    fun skipRest() {
        restJob?.cancel()
        restJob = null
        notifier.cancel()
        _state.update { it?.copy(rest = null) }
    }

    fun adjustRest(deltaSeconds: Int) {
        val rest = _state.value?.rest ?: return
        val newEnd = rest.endsAt + deltaSeconds * 1000L
        if (newEnd <= System.currentTimeMillis()) skipRest() else scheduleRest(newEnd, rest.totalSeconds + deltaSeconds, null)
    }

    /** Persists completed sets. Returns the saved workout id, or null when nothing was logged. */
    suspend fun finish(): Long? {
        val s = _state.value ?: return null
        skipRest()
        val sets = s.exercises.flatMapIndexed { exIndex, ex ->
            ex.sets.mapIndexedNotNull { setIndex, set ->
                if (!set.completed) null else SetLog(
                    workoutId = 0, exerciseId = ex.exercise.id, exerciseName = ex.exercise.name,
                    exerciseOrder = exIndex, setIndex = setIndex, weight = set.weight, reps = set.reps,
                    completed = true, completedAt = set.completedAt,
                )
            }
        }
        _state.value = null
        if (sets.isEmpty()) return null
        return repository.saveWorkout(
            WorkoutLog(routineId = s.routineId, name = s.name, startedAt = s.startedAt, finishedAt = System.currentTimeMillis()),
            sets,
        )
    }

    fun discard() {
        skipRest()
        _state.value = null
    }

    // ---- internals ----
    private suspend fun buildExercise(exercise: Exercise, sets: Int, repMin: Int, repMax: Int, rest: Int): ActiveExercise {
        val previous = repository.previousSets(exercise.id)
        val list = List(sets) { i ->
            val prev = previous.getOrNull(i) ?: previous.lastOrNull()
            ActiveSet(
                weight = prev?.weight ?: 0.0,
                reps = prev?.reps ?: repMin,
                prevWeight = previous.getOrNull(i)?.weight,
                prevReps = previous.getOrNull(i)?.reps,
            )
        }
        return ActiveExercise(exercise, repMin, repMax, rest, list)
    }

    private fun startRest(exIndex: Int, setIndex: Int) {
        val state = _state.value ?: return
        val ex = state.exercises.getOrNull(exIndex) ?: return
        if (ex.restSeconds <= 0) return
        // Last set of the last exercise: nothing to rest for.
        val isLast = exIndex == state.exercises.lastIndex && ex.sets.drop(setIndex + 1).all { it.completed }
        if (isLast) return
        val nextName = if (setIndex < ex.sets.lastIndex) ex.exercise.name else state.exercises.getOrNull(exIndex + 1)?.exercise?.name
        scheduleRest(System.currentTimeMillis() + ex.restSeconds * 1000L, ex.restSeconds, nextName)
    }

    private fun scheduleRest(endsAt: Long, total: Int, nextName: String?) {
        restJob?.cancel()
        _state.update { it?.copy(rest = RestState(endsAt, total)) }
        restJob = scope.launch {
            delay((endsAt - System.currentTimeMillis()).coerceAtLeast(0))
            _state.update { it?.copy(rest = null) }
            notifier.notifyRestOver(nextName)
        }
    }

    private fun mutate(block: (ActiveWorkoutState) -> ActiveWorkoutState) = _state.update { it?.let(block) }

    private fun mutateExercise(exIndex: Int, block: (ActiveExercise) -> ActiveExercise) = mutate { s ->
        s.copy(exercises = s.exercises.mapIndexed { i, ex -> if (i == exIndex) block(ex) else ex })
    }

    private fun updateSetInternal(exIndex: Int, setIndex: Int, block: (ActiveSet) -> ActiveSet) =
        mutateExercise(exIndex) { ex ->
            ex.copy(sets = ex.sets.mapIndexed { i, s -> if (i == setIndex) block(s) else s })
        }
}
