package com.example.nutrease.ui.screens.reminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.ReminderConfig
import com.example.nutrease.ui.components.DaysOfWeekChips
import com.example.nutrease.ui.components.TimeSlotPicker
import com.example.nutrease.ui.components.daysSummaryLabel
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Gestione promemoria diario (RF22): lista dei promemoria del paziente con switch
 * on/off, editor in dialog (giorni + orario + messaggio), FAB "Aggiungi" e azione
 * rapida "uno per pasto". Richiede il permesso runtime POST_NOTIFICATIONS (API 33+)
 * quando esiste almeno un promemoria abilitato.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Risolto a livello di composable (reattivo ai cambi di configurazione/lingua): la
    // stringa non va letta via context dentro la LaunchedEffect (lint LocalContext resources).
    val savedMessage = stringResource(R.string.reminder_saved)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* esito ignorato: l'utente può sempre riabilitare dalle impostazioni */ }

    LaunchedEffect(state.hasEnabledReminder) {
        if (state.hasEnabledReminder && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(state.savedSnackbar) {
        if (state.savedSnackbar) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.consumeSnackbar()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.reminder_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(onClick = viewModel::openNewReminder) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.reminder_add)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.reminder_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (state.reminders.isEmpty()) {
                ReminderEmptyState(onAddMeals = viewModel::addMealPresets, enabled = !state.isSaving)
            } else {
                state.reminders.forEach { reminder ->
                    ReminderListItem(
                        config = reminder,
                        enabled = !state.isSaving,
                        onToggleEnabled = { viewModel.toggleReminderEnabled(reminder) },
                        onEdit = { viewModel.openEditReminder(reminder) },
                        onDelete = { reminder.id?.let(viewModel::deleteReminder) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(72.dp)) // spazio per non finire sotto la FAB
        }
    }

    state.editor?.let { editor ->
        ReminderEditorDialog(
            editor = editor,
            isSaving = state.isSaving,
            onToggleEnabled = viewModel::editorToggleEnabled,
            onToggleDay = viewModel::editorToggleDay,
            onTimeSelected = viewModel::editorUpdateTime,
            onMessageChange = viewModel::editorUpdateMessage,
            onConfirm = viewModel::saveEditor,
            onDismiss = viewModel::closeEditor
        )
    }
}

@Composable
private fun ReminderEmptyState(onAddMeals: () -> Unit, enabled: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.reminder_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onAddMeals, enabled = enabled) {
                Text(stringResource(R.string.reminder_add_meals))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderListItem(
    config: ReminderConfig,
    enabled: Boolean,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = config.time.formatHm(),
                style = MaterialTheme.typography.headlineMedium,
                color = if (config.enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = daysSummaryLabel(config.daysOfWeek),
                style = MaterialTheme.typography.bodyMedium
            )
            config.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!config.enabled) {
                Text(
                    text = stringResource(R.string.reminder_inactive),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(checked = config.enabled, onCheckedChange = { onToggleEnabled() }, enabled = enabled)
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.reminder_delete)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorDialog(
    editor: ReminderEditorState,
    isSaving: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onMessageChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving && editor.daysOfWeek.isNotEmpty()) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
        title = {
            Text(
                stringResource(
                    if (editor.id == null) R.string.reminder_new_title else R.string.reminder_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.reminder_enable_title))
                    Switch(checked = editor.enabled, onCheckedChange = onToggleEnabled)
                }
                Text(
                    text = stringResource(R.string.reminder_days_title),
                    style = MaterialTheme.typography.titleSmall
                )
                DaysOfWeekChips(selected = editor.daysOfWeek, onToggle = onToggleDay)
                TimeSlotPicker(time = editor.time, onTimeSelected = onTimeSelected)
                OutlinedTextField(
                    value = editor.message,
                    onValueChange = onMessageChange,
                    label = { Text(stringResource(R.string.reminder_message_label)) },
                    placeholder = { Text(stringResource(R.string.reminder_message_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

private fun LocalTime.formatHm(): String {
    val h = hour.toString().padStart(2, '0')
    val m = minute.toString().padStart(2, '0')
    return "$h:$m"
}

@Preview(showBackground = true)
@Composable
private fun ReminderScreenPreview() {
    NutreaseTheme { ReminderScreen() }
}
