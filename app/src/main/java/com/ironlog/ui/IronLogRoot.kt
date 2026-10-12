package com.ironlog.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ironlog.di.AppContainer
import com.ironlog.ui.history.HistoryScreen
import com.ironlog.ui.history.WorkoutDetailScreen
import com.ironlog.ui.library.ExerciseEditorScreen
import com.ironlog.ui.library.LibraryScreen
import com.ironlog.ui.routine.RoutineEditorScreen
import com.ironlog.ui.settings.SettingsScreen
import com.ironlog.ui.workout.ActiveWorkoutScreen
import com.ironlog.ui.workouts.WorkoutsScreen

object Routes {
    const val WORKOUTS = "workouts"
    const val HISTORY = "history"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ACTIVE = "active"
    const val ROUTINE = "routine/{id}"
    const val EXERCISE_EDIT = "exercise_edit/{id}"
    const val WORKOUT_DETAIL = "workout_detail/{id}"

    fun routine(id: Long) = "routine/$id"
    fun exerciseEdit(id: String) = "exercise_edit/$id"
    fun workoutDetail(id: Long) = "workout_detail/$id"
    const val NEW_EXERCISE = "new"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.WORKOUTS, "Workouts", Icons.Default.FitnessCenter),
    Tab(Routes.HISTORY, "History & Stats", Icons.Default.Insights),
    Tab(Routes.LIBRARY, "Exercises", Icons.AutoMirrored.Filled.MenuBook),
)

@Composable
fun IronLogRoot(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.WORKOUTS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.WORKOUTS,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.WORKOUTS) {
                WorkoutsScreen(
                    container,
                    onOpenActive = { nav.navigate(Routes.ACTIVE) },
                    onEditRoutine = { nav.navigate(Routes.routine(it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(container, onOpenWorkout = { nav.navigate(Routes.workoutDetail(it)) })
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(container, onEdit = { nav.navigate(Routes.exerciseEdit(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(container, onBack = { nav.popBackStack() }) }
            composable(Routes.ACTIVE) {
                ActiveWorkoutScreen(container, onExit = { nav.popBackStack() })
            }
            composable(Routes.ROUTINE, listOf(navArgument("id") { type = NavType.LongType })) {
                RoutineEditorScreen(container, it.arguments!!.getLong("id"), onClose = { nav.popBackStack() })
            }
            composable(Routes.EXERCISE_EDIT, listOf(navArgument("id") { type = NavType.StringType })) {
                ExerciseEditorScreen(container, it.arguments!!.getString("id")!!, onClose = { nav.popBackStack() })
            }
            composable(Routes.WORKOUT_DETAIL, listOf(navArgument("id") { type = NavType.LongType })) {
                WorkoutDetailScreen(container, it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
        }
    }
}
