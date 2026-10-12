package com.ironlog.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.data.backup.IronLogBackup
import com.ironlog.data.backup.IronLogBackupCodec
import com.ironlog.data.backup.InvalidBackupException
import com.ironlog.di.AppContainer
import com.ironlog.ui.components.ConfirmDialog
import com.ironlog.ui.components.ScreenScaffold
import com.ironlog.ui.containerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun export(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _message.value = try {
            val backup = repo.buildBackup()
            val text = IronLogBackupCodec.encode(backup)
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                    ?: error("Could not open the selected file.")
            }
            "Exported ${backup.workouts.size} workouts, ${backup.routines.size} routines and ${backup.customExercises.size} custom exercises."
        } catch (e: Exception) {
            "Export failed: ${e.message}"
        }
    }

    fun import(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _message.value = try {
            val text = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Could not open the selected file.")
            }
            val backup = IronLogBackupCodec.decode(text)
            repo.restoreBackup(backup)
            "Restored ${backup.workouts.size} workouts, ${backup.routines.size} routines and ${backup.customExercises.size} custom exercises."
        } catch (e: InvalidBackupException) {
            e.message
        } catch (e: Exception) {
            "Import failed: ${e.message}"
        }
    }

    fun clearMessage() { _message.value = null }
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = containerViewModel(container) { SettingsViewModel(it) }
    val message by vm.message.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.export(resolver, uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImport = uri }

    ScreenScaffold(title = "Settings", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Save your workout history, custom routines and custom exercises to a JSON file you control, " +
                            "then restore it on any device. Nothing ever leaves your phone unless you export it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { exportLauncher.launch(IronLogBackup.DEFAULT_FILE_NAME) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Export to ironlog_backup.json") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Restore from backup file") }
                }
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "IronLog 1.0 · 100% local, no account, no cloud.\nExercise data from yuhonas/free-exercise-db (public domain).",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Restore backup?",
            text = "This replaces all current history, routines and custom exercises with the contents of the file.",
            confirmLabel = "Restore",
            onConfirm = { pendingImport = null; vm.clearMessage(); vm.import(resolver, uri) },
            onDismiss = { pendingImport = null },
        )
    }
}
