package com.ironlog.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.data.local.entity.Exercise
import com.ironlog.data.local.entity.Routine
import com.ironlog.data.local.entity.RoutineExercise
import com.ironlog.di.AppContainer
import com.ironlog.ui.components.ExerciseImage
import com.ironlog.ui.components.ExercisePickerDialog
import com.ironlog.ui.components.ScreenScaffold
import com.ironlog.ui.components.Stepper
import com.ironlog.ui.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditItem(
    val exercise: Exercise,
    val sets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val rest: Int = 90,
)

data class EditorState(
    val loaded: Boolean = false,
    val name: String = "",
    val description: String = "",
    val items: List<EditItem> = emptyList(),
)

class RoutineEditorViewModel(container: AppContainer, private val routineId: Long) : ViewModel() {
    private val repo = container.repository
    private var existing: Routine? = null

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    val exercises = repo.exercises

    init {
        viewModelScope.launch {
            if (routineId != 0L) {
                repo.routine(routineId)?.let { r ->
                    existing = r.routine
                    val items = r.exercises.sortedBy { it.position }.mapNotNull { ri ->
                        repo.exercise(ri.exerciseId)?.let { EditItem(it, ri.sets, ri.repMin, ri.repMax, ri.restSeconds) }
                    }
                    _state.value = EditorState(true, r.routine.name, r.routine.description, items)
                    return@launch
                }
            }
            _state.update { it.copy(loaded = true) }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun add(e: Exercise) = _state.update { it.copy(items = it.items + EditItem(e)) }
    fun remove(i: Int) = _state.update { it.copy(items = it.items.filterIndexed { idx, _ -> idx != i }) }

    fun move(i: Int, delta: Int) = _state.update { s ->
        val j = i + delta
        if (j !in s.items.indices) s else s.copy(items = s.items.toMutableList().also { it.add(j, it.removeAt(i)) })
    }

    fun update(i: Int, block: (EditItem) -> EditItem) = _state.update { s ->
        s.copy(items = s.items.mapIndexed { idx, item -> if (idx == i) block(item) else item })
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            val base = existing ?: Routine(name = s.name)
            repo.saveRoutine(
                base.copy(name = s.name.trim(), description = s.description.trim()),
                s.items.map { RoutineExercise(routineId = base.id, exerciseId = it.exercise.id, position = 0, sets = it.sets, repMin = it.repMin, repMax = it.repMax, restSeconds = it.rest) },
            )
            onDone()
        }
    }
}

@Composable
fun RoutineEditorScreen(container: AppContainer, routineId: Long, onClose: () -> Unit) {
    val vm = containerViewModel(container, key = "routine-$routineId") { RoutineEditorViewModel(it, routineId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val allExercises by vm.exercises.collectAsStateWithLifecycle(initialValue = emptyList())
    var picking by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = if (routineId == 0L) "New routine" else "Edit routine",
        onBack = onClose,
        actions = {
            TextButton(onClick = { vm.save(onClose) }, enabled = state.name.isNotBlank()) { Text("Save") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        state.name, vm::setName, label = { Text("Routine name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        state.description, vm::setDescription, label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            itemsIndexed(state.items) { index, item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseImage(item.exercise.imagePath, item.exercise.name, size = 44.dp)
                            Text(
                                item.exercise.name, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            IconButton(onClick = { vm.move(index, -1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "Move up") }
                            IconButton(onClick = { vm.move(index, 1) }, enabled = index < state.items.lastIndex) { Icon(Icons.Default.ArrowDownward, "Move down") }
                            IconButton(onClick = { vm.remove(index) }) { Icon(Icons.Default.Delete, "Remove") }
                        }
                        Stepper("Sets", "${item.sets}", { vm.update(index) { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } }, { vm.update(index) { it.copy(sets = (it.sets + 1).coerceAtMost(12)) } })
                        Stepper(
                            "Min reps", "${item.repMin}",
                            { vm.update(index) { it.copy(repMin = (it.repMin - 1).coerceAtLeast(1)) } },
                            { vm.update(index) { it.copy(repMin = (it.repMin + 1).coerceAtMost(50), repMax = maxOf(it.repMax, it.repMin + 1)) } },
                        )
                        Stepper(
                            "Max reps", "${item.repMax}",
                            { vm.update(index) { it.copy(repMax = (it.repMax - 1).coerceAtLeast(it.repMin)) } },
                            { vm.update(index) { it.copy(repMax = (it.repMax + 1).coerceAtMost(100)) } },
                        )
                        Stepper(
                            "Rest", "${item.rest}s",
                            { vm.update(index) { it.copy(rest = (it.rest - 15).coerceAtLeast(0)) } },
                            { vm.update(index) { it.copy(rest = (it.rest + 15).coerceAtMost(600)) } },
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.Add, null)
                    Text("  Add exercise")
                }
            }
        }
    }

    if (picking) {
        ExercisePickerDialog(allExercises, onPick = { vm.add(it); picking = false }, onDismiss = { picking = false })
    }
}
