package com.ironlog.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.data.local.entity.Exercise
import com.ironlog.di.AppContainer
import com.ironlog.domain.session.ActiveExercise
import com.ironlog.domain.session.ActiveSet
import com.ironlog.domain.session.ActiveWorkoutState
import com.ironlog.domain.session.RestState
import com.ironlog.ui.components.ConfirmDialog
import com.ironlog.ui.components.ExerciseDetailSheet
import com.ironlog.ui.components.ExerciseImage
import com.ironlog.ui.components.ExercisePickerDialog
import com.ironlog.ui.components.ScreenScaffold
import com.ironlog.ui.components.formatClock
import com.ironlog.ui.components.formatWeight
import com.ironlog.ui.containerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActiveWorkoutViewModel(container: AppContainer) : ViewModel() {
    val manager = container.activeWorkout
    val state = manager.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), manager.state.value)
    val exercises = container.repository.exercises

    fun addExercise(e: Exercise) { viewModelScope.launch { manager.addExercise(e) } }
    fun finish(onDone: () -> Unit) { viewModelScope.launch { manager.finish(); onDone() } }
}

@Composable
fun ActiveWorkoutScreen(container: AppContainer, onExit: () -> Unit) {
    val vm = containerViewModel(container) { ActiveWorkoutViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val allExercises by vm.exercises.collectAsStateWithLifecycle(initialValue = emptyList())
    val s = state

    // Session ended (finished or discarded): leave the screen.
    LaunchedEffect(s == null) { if (s == null) onExit() }
    if (s == null) return

    val manager = vm.manager
    var detail by remember { mutableStateOf<Exercise?>(null) }
    var picking by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(500) }
    }

    ScreenScaffold(
        title = s.name,
        onBack = onExit,
        actions = {
            Text(formatClock(((now - s.startedAt) / 1000).coerceAtLeast(0)), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { confirmFinish = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Finish") }
        },
        bottomBar = {
            s.rest?.let { RestBanner(it, now, onAdjust = manager::adjustRest, onSkip = manager::skipRest) }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(s.exercises) { exIndex, ex ->
                ExerciseCard(
                    ex,
                    onInfo = { detail = ex.exercise },
                    onRemove = { manager.removeExercise(exIndex) },
                    onAddSet = { manager.addSet(exIndex) },
                    onWeight = { si, w -> manager.updateSet(exIndex, si, weight = w) },
                    onReps = { si, r -> manager.updateSet(exIndex, si, reps = r) },
                    onToggle = { si -> manager.toggleSet(exIndex, si) },
                    onRemoveSet = { si -> manager.removeSet(exIndex, si) },
                )
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.Add, null)
                    Text("  Add exercise")
                }
            }
            item {
                TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Discard workout", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    detail?.let { ExerciseDetailSheet(it, onDismiss = { detail = null }) }
    if (picking) {
        ExercisePickerDialog(allExercises, onPick = { vm.addExercise(it); picking = false }, onDismiss = { picking = false })
    }
    if (confirmFinish) {
        val remaining = s.totalSets - s.completedSets
        ConfirmDialog(
            title = "Finish workout?",
            text = if (remaining > 0) "$remaining set(s) are not completed and will not be logged." else "Great session! Save it to your history.",
            confirmLabel = "Finish",
            onConfirm = { confirmFinish = false; vm.finish { } },
            onDismiss = { confirmFinish = false },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            text = "Nothing from this session will be saved.",
            confirmLabel = "Discard",
            onConfirm = { confirmDiscard = false; manager.discard() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun RestBanner(rest: RestState, now: Long, onAdjust: (Int) -> Unit, onSkip: () -> Unit) {
    val remaining = ((rest.endsAt - now) / 1000).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("REST", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(formatClock(remaining), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            OutlinedButton(onClick = { onAdjust(-15) }) { Text("-15s") }
            OutlinedButton(onClick = { onAdjust(15) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("+15s") }
            Button(onClick = onSkip) { Text("Skip") }
        }
    }
}

@Composable
private fun ExerciseCard(
    ex: ActiveExercise,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onWeight: (Int, Double) -> Unit,
    onReps: (Int, Int) -> Unit,
    onToggle: (Int) -> Unit,
    onRemoveSet: (Int) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.clickable(onClick = onInfo), verticalAlignment = Alignment.CenterVertically) {
                ExerciseImage(ex.exercise.imagePath, ex.exercise.name, size = 48.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(ex.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Target ${ex.repMin}–${ex.repMax} reps · rest ${ex.restSeconds}s",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onInfo) { Icon(Icons.Default.Info, "How to perform") }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove exercise") }
            }
            Row(Modifier.fillMaxWidth()) {
                HeaderCell("SET", Modifier.width(36.dp))
                HeaderCell("PREVIOUS", Modifier.weight(1.1f))
                HeaderCell("KG", Modifier.weight(1f))
                HeaderCell("REPS", Modifier.weight(1f))
                Box(Modifier.width(56.dp))
            }
            ex.sets.forEachIndexed { i, set ->
                SetRow(
                    index = i, set = set, repMax = ex.repMax,
                    onWeight = { onWeight(i, it) }, onReps = { onReps(i, it) },
                    onToggle = { onToggle(i) }, onRemove = { onRemoveSet(i) },
                    canRemove = ex.sets.size > 1,
                )
            }
            TextButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text("  Add set")
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SetRow(
    index: Int,
    set: ActiveSet,
    repMax: Int,
    onWeight: (Double) -> Unit,
    onReps: (Int) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
    val rowColor = if (set.completed) MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(rowColor).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${index + 1}", Modifier.width(30.dp).clickable(enabled = canRemove && !set.completed, onClick = onRemove),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (set.prevWeight != null && set.prevReps != null) "${formatWeight(set.prevWeight)}×${set.prevReps}" else "–",
            Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberField(set.weight, decimal = true, modifier = Modifier.weight(1f)) { onWeight(it) }
        NumberField(set.reps.toDouble(), decimal = false, modifier = Modifier.weight(1f)) { onReps(it.toInt()) }
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(if (set.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check, if (set.completed) "Mark set incomplete" else "Complete set",
                tint = if (set.completed) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Numeric text field that keeps in-progress input like "6." intact while editing. */
@Composable
private fun NumberField(value: Double, decimal: Boolean, modifier: Modifier, onValue: (Double) -> Unit) {
    fun fmt(v: Double) = if (decimal) (if (v == 0.0) "" else formatWeight(v)) else v.toLong().toString()
    var text by remember { mutableStateOf(fmt(value)) }
    LaunchedEffect(value) { if ((text.toDoubleOrNull() ?: 0.0) != value) text = fmt(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val cleaned = input.replace(',', '.')
            if (cleaned.isEmpty() || cleaned.toDoubleOrNull() != null || (decimal && cleaned.endsWith("."))) {
                if (!decimal && cleaned.contains('.')) return@OutlinedTextField
                text = cleaned
                onValue(cleaned.toDoubleOrNull() ?: 0.0)
            }
        },
        singleLine = true,
        placeholder = { Text("0", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
