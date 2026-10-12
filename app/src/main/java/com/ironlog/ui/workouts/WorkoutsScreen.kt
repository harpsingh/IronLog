package com.ironlog.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.data.local.dao.RoutineWithExercises
import com.ironlog.di.AppContainer
import com.ironlog.ui.components.ConfirmDialog
import com.ironlog.ui.components.ScreenScaffold
import com.ironlog.ui.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineCardUi(val routine: RoutineWithExercises, val exerciseNames: List<String>)

class WorkoutsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val manager = container.activeWorkout

    val routines: StateFlow<List<RoutineCardUi>> = combine(repo.routines, repo.exercises) { routines, exercises ->
        val names = exercises.associate { it.id to it.name }
        routines.map { r ->
            RoutineCardUi(r, r.exercises.sortedBy { it.position }.mapNotNull { names[it.exerciseId] })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val active = manager.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), manager.state.value)

    fun start(routineId: Long, onStarted: () -> Unit) {
        viewModelScope.launch {
            manager.startFromRoutine(routineId)
            onStarted()
        }
    }

    fun quickStart(onStarted: () -> Unit) {
        manager.startEmpty()
        onStarted()
    }

    fun delete(routineId: Long) {
        viewModelScope.launch { repo.deleteRoutine(routineId) }
    }
}

@Composable
fun WorkoutsScreen(
    container: AppContainer,
    onOpenActive: () -> Unit,
    onEditRoutine: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = containerViewModel(container) { WorkoutsViewModel(it) }
    val routines by vm.routines.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<RoutineWithExercises?>(null) }

    ScreenScaffold(
        title = "Workouts",
        onBack = null,
        actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditRoutine(0) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New routine") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val a = active
                if (a != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Workout in progress", style = MaterialTheme.typography.titleMedium)
                            Text("${a.name} · ${a.completedSets}/${a.totalSets} sets done")
                            Button(onClick = onOpenActive, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Resume") }
                        }
                    }
                } else {
                    Button(
                        onClick = { vm.quickStart(onOpenActive) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text("  Quick start empty workout", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item { Text("Your routines", style = MaterialTheme.typography.titleMedium) }
            items(routines, key = { it.routine.routine.id }) { ui ->
                RoutineCard(
                    ui,
                    workoutActive = active != null,
                    onStart = { vm.start(ui.routine.routine.id, onOpenActive) },
                    onEdit = { onEditRoutine(ui.routine.routine.id) },
                    onDelete = { toDelete = ui.routine },
                )
            }
        }
    }

    toDelete?.let { r ->
        ConfirmDialog(
            title = "Delete routine?",
            text = "\"${r.routine.name}\" will be removed. Logged workouts are kept.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(r.routine.id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun RoutineCard(
    ui: RoutineCardUi,
    workoutActive: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val routine = ui.routine.routine
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${ui.routine.exercises.size} exercises · ${ui.routine.exercises.sumOf { it.sets }} sets",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit routine") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete routine") }
            }
            if (routine.description.isNotBlank()) {
                Text(routine.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                ui.exerciseNames.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (workoutActive) {
                OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Resume current workout") }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("  Start workout")
                }
            }
        }
    }
}
