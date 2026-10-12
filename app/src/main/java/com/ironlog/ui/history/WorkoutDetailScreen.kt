package com.ironlog.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironlog.data.local.entity.WorkoutLog
import com.ironlog.di.AppContainer
import com.ironlog.domain.stats.StatsCalculator
import com.ironlog.ui.components.ConfirmDialog
import com.ironlog.ui.components.ScreenScaffold
import com.ironlog.ui.components.formatDuration
import com.ironlog.ui.components.formatWeight
import com.ironlog.ui.containerViewModel
import kotlinx.coroutines.launch

class WorkoutDetailViewModel(container: AppContainer, private val id: Long) : ViewModel() {
    private val repo = container.repository
    val sets = repo.setsFor(id)
    suspend fun load(): WorkoutLog? = repo.workout(id)
    fun delete(onDone: () -> Unit) { viewModelScope.launch { repo.deleteWorkout(id); onDone() } }
}

@Composable
fun WorkoutDetailScreen(container: AppContainer, id: Long, onBack: () -> Unit) {
    val vm = containerViewModel(container, key = "workout-$id") { WorkoutDetailViewModel(it, id) }
    val sets by vm.sets.collectAsStateWithLifecycle(initialValue = emptyList())
    val workout by produceState<WorkoutLog?>(null) { value = vm.load() }
    var confirmDelete by remember { mutableStateOf(false) }
    val grouped = sets.filter { it.completed }.groupBy { it.exerciseName }

    ScreenScaffold(
        title = workout?.name ?: "Workout",
        onBack = onBack,
        actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete workout") } },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            workout?.let { w ->
                item {
                    val duration = w.finishedAt?.let { formatDuration(it - w.startedAt) } ?: "–"
                    Text(
                        "${StatsCalculator.toDate(w.startedAt)} · $duration · ${formatWeight(StatsCalculator.volumeOf(sets))} kg lifted",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(grouped.entries.toList(), key = { it.key }) { (name, list) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        list.sortedBy { it.setIndex }.forEachIndexed { i, s ->
                            Row {
                                Text("Set ${i + 1}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatWeight(s.weight)} kg × ${s.reps}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete workout?", text = "This removes the session from your history.", confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; vm.delete(onBack) }, onDismiss = { confirmDelete = false },
        )
    }
}
