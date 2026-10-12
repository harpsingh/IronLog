package com.ironlog.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.data.local.entity.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val imageCache = LruCache<String, ImageBitmap>(48)

/** Bundled exercise thumbnail (from assets) with a letter placeholder when none exists. */
@Composable
fun ExerciseImage(path: String?, name: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(imageCache.get(path ?: ""), path) {
        if (path == null) return@produceState
        value = imageCache.get(path) ?: withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }?.also { imageCache.put(path, it) }
    }
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Text(
        text = text.replaceFirstChar { it.titlecase(Locale.getDefault()) },
        style = MaterialTheme.typography.labelMedium,
        color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Big +/- control used for sets, reps and rest durations. */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Remove, "Decrease") }
        Text(
            value, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Add, "Increase") }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Searchable exercise chooser shown as a dialog. */
@Composable
fun ExercisePickerDialog(exercises: List<Exercise>, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, exercises) { exercises.filterBySearch(query).take(100) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search exercises") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.height(380.dp)) {
                    items(filtered, key = { it.id }) { ex ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(ex) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseImage(ex.imagePath, ex.name, size = 44.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(ex.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    ex.primaryMuscles.joinToString(), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

fun List<Exercise>.filterBySearch(query: String): List<Exercise> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return this
    val terms = q.split(' ').filter { it.isNotEmpty() }
    return filter { ex ->
        val haystack = (ex.name + " " + ex.primaryMuscles.joinToString(" ") + " " + ex.equipment).lowercase()
        terms.all { it in haystack }
    }
}

/** Bottom sheet with image, tags and step-by-step instructions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (onEdit != null) FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                if (onDelete != null) FilledTonalIconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
            if (exercise.imagePath != null) {
                ExerciseImage(exercise.imagePath, exercise.name, Modifier.fillMaxWidth().height(220.dp), size = 220.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                exercise.primaryMuscles.forEach { Tag(it, highlighted = true) }
                Tag(exercise.equipment)
                Tag(exercise.level)
            }
            if (exercise.secondaryMuscles.isNotEmpty()) {
                Text(
                    "Also works: " + exercise.secondaryMuscles.joinToString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
            SectionTitle("Instructions")
            if (exercise.instructions.isEmpty()) {
                Text("No instructions added.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            exercise.instructions.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    Text(step, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

fun formatWeight(w: Double): String = if (w % 1.0 == 0.0) w.toLong().toString() else String.format(Locale.US, "%.1f", w)

fun formatDuration(millis: Long): String {
    val totalMin = millis / 60_000
    return if (totalMin >= 60) "${totalMin / 60}h ${totalMin % 60}m" else "${totalMin}m"
}

fun formatClock(seconds: Long): String = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
