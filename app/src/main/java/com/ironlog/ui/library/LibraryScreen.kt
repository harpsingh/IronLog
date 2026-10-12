package com.ironlog.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.data.local.entity.Exercise
import com.ironlog.di.AppContainer
import com.ironlog.ui.Routes
import com.ironlog.ui.components.ConfirmDialog
import com.ironlog.ui.components.EmptyState
import com.ironlog.ui.components.ExerciseDetailSheet
import com.ironlog.ui.components.ExerciseImage
import com.ironlog.ui.components.ScreenScaffold
import com.ironlog.ui.components.Tag
import com.ironlog.ui.components.filterBySearch
import com.ironlog.ui.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val MUSCLES = listOf(
    "abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms", "glutes",
    "hamstrings", "lats", "lower back", "middle back", "neck", "quadriceps", "shoulders", "traps", "triceps",
)

class LibraryViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val exercises: StateFlow<List<Exercise>> =
        repo.exercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) { viewModelScope.launch { repo.deleteCustomExercise(id) } }
}

@Composable
fun LibraryScreen(container: AppContainer, onEdit: (String) -> Unit) {
    val vm = containerViewModel(container) { LibraryViewModel(it) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var customOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Exercise?>(null) }
    var toDelete by remember { mutableStateOf<Exercise?>(null) }

    val filtered = remember(all, query, muscle, customOnly) {
        all.filterBySearch(query)
            .filter { muscle == null || muscle in it.primaryMuscles || muscle in it.secondaryMuscles }
            .filter { !customOnly || it.isCustom }
    }

    ScreenScaffold(
        title = "Exercise Library",
        onBack = null,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(Routes.NEW_EXERCISE) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Custom exercise") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search ${all.size} exercises") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(customOnly, { customOnly = !customOnly }, label = { Text("My exercises") }) }
                item { FilterChip(muscle == null, { muscle = null }, label = { Text("All muscles") }) }
                items(MUSCLES) { m ->
                    FilterChip(muscle == m, { muscle = if (muscle == m) null else m }, label = { Text(m.replaceFirstChar { it.titlecase() }) })
                }
            }
            if (filtered.isEmpty()) {
                EmptyState("No exercises match your filters.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { ex ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = ex }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseImage(ex.imagePath, ex.name)
                            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(ex.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ex.primaryMuscles.take(2).forEach { Tag(it, highlighted = true) }
                                    Tag(ex.equipment)
                                    if (ex.isCustom) Tag("custom")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { ex ->
        ExerciseDetailSheet(
            ex,
            onDismiss = { selected = null },
            onEdit = if (ex.isCustom) ({ selected = null; onEdit(ex.id) }) else null,
            onDelete = if (ex.isCustom) ({ toDelete = ex; selected = null }) else null,
        )
    }
    toDelete?.let { ex ->
        ConfirmDialog(
            title = "Delete exercise?",
            text = "\"${ex.name}\" will be removed from the library and from any routines using it. Logged history is kept.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(ex.id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}
