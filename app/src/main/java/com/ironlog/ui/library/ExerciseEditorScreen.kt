package com.ironlog.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.data.local.entity.Exercise
import com.ironlog.di.AppContainer
import com.ironlog.ui.Routes
import com.ironlog.ui.components.ScreenScaffold
import com.ironlog.ui.components.SectionTitle
import com.ironlog.ui.containerViewModel
import kotlinx.coroutines.launch

private val EQUIPMENT = listOf("none", "body only", "dumbbell", "barbell", "cable", "machine", "kettlebells", "bands", "other")
private val LEVELS = listOf("beginner", "intermediate", "expert")

class ExerciseEditorViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    suspend fun load(id: String): Exercise? = repo.exercise(id)
    fun save(exercise: Exercise, onDone: () -> Unit) {
        viewModelScope.launch { repo.saveCustomExercise(exercise); onDone() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorScreen(container: AppContainer, id: String, onClose: () -> Unit) {
    val vm = containerViewModel(container, key = "ex-$id") { ExerciseEditorViewModel(it) }
    val isNew = id == Routes.NEW_EXERCISE
    var original by remember { mutableStateOf<Exercise?>(null) }
    var name by remember { mutableStateOf("") }
    var primary by remember { mutableStateOf(setOf<String>()) }
    var equipment by remember { mutableStateOf("dumbbell") }
    var level by remember { mutableStateOf("beginner") }
    var steps by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (!isNew) vm.load(id)?.let {
            original = it; name = it.name; primary = it.primaryMuscles.toSet()
            equipment = it.equipment; level = it.level; steps = it.instructions.joinToString("\n")
        }
    }

    ScreenScaffold(
        title = if (isNew) "New exercise" else "Edit exercise",
        onBack = onClose,
        actions = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = original
                    vm.save(
                        Exercise(
                            id = base?.id ?: "",
                            name = name.trim(),
                            category = base?.category ?: "strength",
                            level = level,
                            equipment = equipment,
                            mechanic = base?.mechanic,
                            force = base?.force,
                            primaryMuscles = MUSCLES.filter { it in primary },
                            secondaryMuscles = base?.secondaryMuscles.orEmpty(),
                            instructions = steps.lines().map { it.trim() }.filter { it.isNotEmpty() },
                            imagePath = null,
                            isCustom = true,
                        ),
                        onClose,
                    )
                },
            ) { Text("Save") }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionTitle("Target muscles")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MUSCLES.forEach { m ->
                    FilterChip(m in primary, { primary = if (m in primary) primary - m else primary + m }, label = { Text(m) })
                }
            }
            SectionTitle("Equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EQUIPMENT.forEach { e -> FilterChip(equipment == e, { equipment = e }, label = { Text(e) }) }
            }
            SectionTitle("Level")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEVELS.forEach { l -> FilterChip(level == l, { level = l }, label = { Text(l) }) }
            }
            SectionTitle("Instructions")
            OutlinedTextField(
                steps, { steps = it }, modifier = Modifier.fillMaxWidth(), minLines = 5,
                label = { Text("One step per line") },
            )
            Text(
                "Custom exercises are stored only on this device.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
