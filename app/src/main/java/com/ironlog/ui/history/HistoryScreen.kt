package com.ironlog.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.data.local.entity.SetLog
import com.ironlog.data.local.entity.WorkoutLog
import com.ironlog.di.AppContainer
import com.ironlog.domain.stats.StatsCalculator
import com.ironlog.ui.components.BarChart
import com.ironlog.ui.components.EmptyState
import com.ironlog.ui.components.LineChart
import com.ironlog.ui.components.ScreenScaffold
import com.ironlog.ui.components.SectionTitle
import com.ironlog.ui.components.formatDuration
import com.ironlog.ui.components.formatWeight
import com.ironlog.ui.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class HistoryViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val workouts: StateFlow<List<WorkoutLog>> =
        repo.finishedWorkouts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sets: StateFlow<List<SetLog>> =
        repo.completedSets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

@Composable
fun HistoryScreen(container: AppContainer, onOpenWorkout: (Long) -> Unit) {
    val vm = containerViewModel(container) { HistoryViewModel(it) }
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val volumes = remember(sets) { sets.groupBy { it.workoutId }.mapValues { StatsCalculator.volumeOf(it.value) } }

    ScreenScaffold(title = "History & Stats", onBack = null) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Log", "Calendar", "Charts").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            if (workouts.isEmpty()) {
                EmptyState("No workouts yet.\nFinish your first session to see your history and charts.")
            } else when (tab) {
                0 -> LogTab(workouts, volumes, onOpenWorkout)
                1 -> CalendarTab(workouts, volumes, onOpenWorkout)
                else -> ChartsTab(workouts, sets)
            }
        }
    }
}

@Composable
fun WorkoutRow(w: WorkoutLog, volume: Double, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(StatsCalculator.toDate(w.startedAt).format(dateFormat), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(w.name, style = MaterialTheme.typography.titleMedium)
            val duration = w.finishedAt?.let { formatDuration(it - w.startedAt) } ?: "–"
            Text(
                "$duration · ${formatWeight(volume)} kg lifted",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogTab(workouts: List<WorkoutLog>, volumes: Map<Long, Double>, onOpen: (Long) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(workouts, key = { it.id }) { w -> WorkoutRow(w, volumes[w.id] ?: 0.0) { onOpen(w.id) } }
    }
}

@Composable
private fun CalendarTab(workouts: List<WorkoutLog>, volumes: Map<Long, Double>, onOpen: (Long) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val byDay = remember(workouts) { workouts.groupBy { StatsCalculator.toDate(it.startedAt) } }
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.values().forEach {
                    Text(
                        it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val cells = leading + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        items(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - leading + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            val trained = byDay.containsKey(date)
                            val isSelected = selected == date
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .background(
                                        when {
                                            trained -> MaterialTheme.colorScheme.primary
                                            isSelected -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        }
                                    )
                                    .clickable { selected = date },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$dayNum",
                                    color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
        val day = selected
        if (day != null) {
            item { SectionTitle(day.format(dateFormat)) }
            val list = byDay[day].orEmpty()
            if (list.isEmpty()) item { Text("Rest day", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it.id }) { w -> WorkoutRow(w, volumes[w.id] ?: 0.0) { onOpen(w.id) } }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun ChartsTab(workouts: List<WorkoutLog>, sets: List<SetLog>) {
    val volume = remember(workouts, sets) { StatsCalculator.volumePerWorkout(workouts, sets).takeLast(20) }
    val weekly = remember(workouts) { StatsCalculator.workoutsPerWeek(workouts) }
    val streak = remember(workouts) { StatsCalculator.weekStreak(workouts) }
    val totalVolume = remember(sets) { StatsCalculator.volumeOf(sets) }
    val logged = remember(sets) {
        sets.groupBy { it.exerciseId }.map { (id, list) -> id to list.first().exerciseName }.sortedBy { it.second }
    }
    var exerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedId = exerciseId ?: logged.firstOrNull()?.first

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Workouts", "${workouts.size}", Modifier.weight(1f))
                StatTile("Week streak", "$streak", Modifier.weight(1f))
                StatTile("Total kg", if (totalVolume >= 1000) "${formatWeight(Math.round(totalVolume / 100.0) / 10.0)}k" else formatWeight(totalVolume), Modifier.weight(1f))
            }
        }
        item { ChartCard("Total weight lifted per workout (kg)") { LineChart(volume) } }
        item { ChartCard("Workouts per week") { BarChart(weekly) } }
        item {
            ChartCard("Strength progress (est. 1RM, kg)") {
                if (logged.isEmpty()) {
                    Text("Complete some sets to see progress", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        items(logged, key = { it.first }) { (id, name) ->
                            FilterChip(selected = id == selectedId, onClick = { exerciseId = id }, label = { Text(name) })
                        }
                    }
                    val series = remember(workouts, sets, selectedId) {
                        selectedId?.let { StatsCalculator.strengthProgress(workouts, sets, it) }.orEmpty()
                    }
                    LineChart(series, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
