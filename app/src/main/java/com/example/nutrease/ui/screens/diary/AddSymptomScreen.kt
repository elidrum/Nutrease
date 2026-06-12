package com.example.nutrease.ui.screens.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.model.SymptomType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Form sintomo (RF10/RF12): tipologia, severità, data e ora. In fondo al file le
 * etichette italiane di [SymptomType]/[SymptomSeverity], `internal` perché riusate
 * anche dalle schermate dello specialista (stesso modulo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSymptomScreen(
    initialDate: LocalDate?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddSymptomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialDate) {
        if (initialDate != null) viewModel.setInitialDate(initialDate)
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    AddSymptomContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onUpdateDate = viewModel::updateDate,
        onUpdateTime = viewModel::updateTime,
        onUpdateType = viewModel::updateType,
        onUpdateSeverity = viewModel::updateSeverity,
        onUpdateNote = viewModel::updateNote,
        onSave = viewModel::save
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Falso positivo dell'IDE: showDatePicker/showTimePicker sono riassegnati nelle callback
// dei picker e riletti alla ricomposizione (stesso pattern di DiaryScreen).
@Suppress("AssignedValueIsNeverRead")
private fun AddSymptomContent(
    uiState: AddSymptomUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onUpdateDate: (LocalDate) -> Unit,
    onUpdateTime: (LocalTime) -> Unit,
    onUpdateType: (SymptomType) -> Unit,
    onUpdateSeverity: (SymptomSeverity) -> Unit,
    onUpdateNote: (String) -> Unit,
    onSave: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(if (uiState.isEditing) R.string.add_symptom_title_edit else R.string.add_symptom_title_new)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoadingExisting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DateTimeRowSymptom(
                date = uiState.date,
                time = uiState.time,
                onPickDate = { showDatePicker = true },
                onPickTime = { showTimePicker = true }
            )

            HorizontalDivider()

            Text(stringResource(R.string.add_symptom_type_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SymptomTypeSelector(selected = uiState.type, onSelected = onUpdateType)

            if (uiState.type == SymptomType.OTHER) {
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = onUpdateNote,
                    label = { Text(stringResource(R.string.add_symptom_other_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            Text(stringResource(R.string.add_symptom_severity_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SeveritySelector(selected = uiState.severity, onSelected = onUpdateSeverity)

            Spacer(Modifier.padding(top = 8.dp))

            if (uiState.isSaving) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(if (uiState.isEditing) R.string.add_symptom_update else R.string.add_symptom_save))
                }
            }
        }
    }

    if (showDatePicker) {
        SymptomDatePickerSheet(
            initialDate = uiState.date,
            onDateSelected = { onUpdateDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        SymptomTimePickerSheet(
            initialTime = uiState.time,
            onTimeSelected = { onUpdateTime(it); showTimePicker = false },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun DateTimeRowSymptom(
    date: LocalDate,
    time: LocalTime,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = formatDate(date),
            onValueChange = {},
            label = { Text(stringResource(R.string.common_date)) },
            readOnly = true,
            enabled = false, // necessario perché il click arrivi al modifier .clickable
            trailingIcon = {
                Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.common_date))
            },
            colors = pickerFieldColors(),
            modifier = Modifier.weight(1f).clickable { onPickDate() }
        )
        OutlinedTextField(
            value = formatTime(time.hour, time.minute),
            onValueChange = {},
            label = { Text(stringResource(R.string.common_time)) },
            readOnly = true,
            enabled = false,
            trailingIcon = {
                Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.common_time))
            },
            colors = pickerFieldColors(),
            modifier = Modifier.weight(1f).clickable { onPickTime() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymptomTypeSelector(
    selected: SymptomType,
    onSelected: (SymptomType) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SymptomType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelected(type) },
                label = { Text(symptomTypeLabel(type)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeveritySelector(
    selected: SymptomSeverity,
    onSelected: (SymptomSeverity) -> Unit
) {
    val selectable = remember {
        listOf(SymptomSeverity.MILD, SymptomSeverity.MODERATE, SymptomSeverity.SEVERE)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        selectable.forEach { sev ->
            FilterChip(
                selected = sev == selected,
                onClick = { onSelected(sev) },
                label = { Text(severityLabel(sev)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymptomDatePickerSheet(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = initialDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.fromEpochMilliseconds(millis)
                        .toLocalDateTime(TimeZone.UTC).date
                    onDateSelected(date)
                }
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymptomTimePickerSheet(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimePicker(state = state)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    TextButton(onClick = { onTimeSelected(LocalTime(state.hour, state.minute)) }) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

/**
 * Etichetta da mostrare per un sintomo registrato: se è di tipo "Altro" con una nota
 * libera, usa quella; altrimenti l'etichetta standard del tipo. Per le aggregazioni
 * (es. statistiche, che ragionano per categoria) si usa direttamente [symptomTypeLabel].
 */
@Composable
internal fun symptomDisplayLabel(symptom: Symptom): String =
    symptom.note?.takeIf { symptom.type == SymptomType.OTHER && it.isNotBlank() }
        ?: symptomTypeLabel(symptom.type)

@Composable
internal fun symptomTypeLabel(type: SymptomType): String = when (type) {
    SymptomType.BLOATING -> stringResource(R.string.symptom_type_bloating)
    SymptomType.CRAMPS -> stringResource(R.string.symptom_type_cramps)
    SymptomType.DIARRHEA -> stringResource(R.string.symptom_type_diarrhea)
    SymptomType.CONSTIPATION -> stringResource(R.string.symptom_type_constipation)
    SymptomType.NAUSEA -> stringResource(R.string.symptom_type_nausea)
    SymptomType.REFLUX -> stringResource(R.string.symptom_type_reflux)
    SymptomType.OTHER -> stringResource(R.string.symptom_type_other)
}

@Composable
internal fun severityLabel(severity: SymptomSeverity): String = when (severity) {
    SymptomSeverity.NONE -> stringResource(R.string.severity_none)
    SymptomSeverity.MILD -> stringResource(R.string.severity_mild)
    SymptomSeverity.MODERATE -> stringResource(R.string.severity_moderate)
    SymptomSeverity.SEVERE -> stringResource(R.string.severity_severe)
}