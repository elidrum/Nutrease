package com.example.nutrease.ui.screens.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.model.MealType
import com.example.nutrease.domain.model.NutrientTotals
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.ui.components.dayShortLabel
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private sealed class DiaryEntry(val timeKey: Int) {
    data class MealEntry(val meal: Meal) : DiaryEntry(meal.time.hour * 60 + meal.time.minute)
    data class SymptomEntry(val symptom: Symptom) : DiaryEntry(symptom.time.hour * 60 + symptom.time.minute)
}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Schermata del diario giornaliero (RF11–RF12): strip orizzontale dei giorni (con
 * calendario per salti lunghi), timeline mista pasti+sintomi ordinata per ora, FAB con
 * menu "Aggiungi pasto/sintomo", dialog di conferma eliminazione. Il refresh al ritorno
 * da add/edit avviene con [LifecycleStartEffect] (si riesegue a ogni ON_START).
 */
// Falso positivo dell'IDE: gli stati dei dialog/menu (showDatePicker, pendingDelete*, fabMenuOpen)
// vengono riassegnati nelle callback di dismiss/conferma e riletti alla ricomposizione, che
// l'analisi statica non vede (stesso pattern di DateRangeSelector).
@Suppress("AssignedValueIsNeverRead")
@Composable
fun DiaryScreen(
    onNavigateBack: () -> Unit,
    onAddMeal: (LocalDate) -> Unit,
    onAddSymptom: (LocalDate) -> Unit,
    onEditMeal: (Long) -> Unit,
    onEditSymptom: (Long) -> Unit,
    viewModel: DiaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDeleteMeal by remember { mutableStateOf<Long?>(null) }
    var pendingDeleteSymptom by remember { mutableStateOf<Long?>(null) }
    var fabMenuOpen by remember { mutableStateOf(false) }

    LifecycleStartEffect(Unit) {
        viewModel.refresh()
        onStopOrDispose { }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    val entries = remember(uiState.meals, uiState.symptoms) {
        (uiState.meals.map { DiaryEntry.MealEntry(it) } +
            uiState.symptoms.map { DiaryEntry.SymptomEntry(it) })
            .sortedBy { it.timeKey }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.diary_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { fabMenuOpen = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.diary_add_entry))
                }
                DropdownMenu(
                    expanded = fabMenuOpen,
                    onDismissRequest = { fabMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.diary_add_meal)) },
                        leadingIcon = { Icon(Icons.Default.Restaurant, contentDescription = null) },
                        onClick = {
                            fabMenuOpen = false
                            onAddMeal(uiState.selectedDate)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.diary_add_symptom)) },
                        leadingIcon = { Icon(Icons.Default.HealthAndSafety, contentDescription = null) },
                        onClick = {
                            fabMenuOpen = false
                            onAddSymptom(uiState.selectedDate)
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DiaryDayStrip(
                selectedDate = uiState.selectedDate,
                onSelectDate = viewModel::selectDate,
                onPickDate = { showDatePicker = true }
            )

            if (uiState.meals.isNotEmpty()) {
                DailyTotalsCard(uiState.meals.fold(NutrientTotals.Zero) { acc, m -> acc + m.totals })
            }

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    entries.isEmpty() -> Text(
                        text = stringResource(R.string.diary_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = entries,
                            key = { entry ->
                                when (entry) {
                                    is DiaryEntry.MealEntry -> "meal-${entry.meal.id ?: entry.hashCode()}"
                                    is DiaryEntry.SymptomEntry -> "symptom-${entry.symptom.id ?: entry.hashCode()}"
                                }
                            }
                        ) { entry ->
                            when (entry) {
                                is DiaryEntry.MealEntry -> MealCard(
                                    meal = entry.meal,
                                    onEdit = { entry.meal.id?.let(onEditMeal) },
                                    onDelete = { pendingDeleteMeal = entry.meal.id }
                                )
                                is DiaryEntry.SymptomEntry -> SymptomCard(
                                    symptom = entry.symptom,
                                    onEdit = { entry.symptom.id?.let(onEditSymptom) },
                                    onDelete = { pendingDeleteSymptom = entry.symptom.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DateSelectorDialog(
            initialDate = uiState.selectedDate,
            onDateSelected = { date ->
                viewModel.selectDate(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    pendingDeleteMeal?.let { mealId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMeal = null },
            title = { Text(stringResource(R.string.diary_delete_meal_title)) },
            text = { Text(stringResource(R.string.diary_delete_meal_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMeal(mealId)
                    pendingDeleteMeal = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMeal = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    pendingDeleteSymptom?.let { symptomId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSymptom = null },
            title = { Text(stringResource(R.string.diary_delete_symptom_title)) },
            text = { Text(stringResource(R.string.diary_delete_symptom_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSymptom(symptomId)
                    pendingDeleteSymptom = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSymptom = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

private const val DAY_STRIP_BACK = 14
private const val DAY_STRIP_FORWARD = 7

@Composable
private fun DiaryDayStrip(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Finestra di giorni centrata sulla data selezionata: il tap su una cella adiacente
    // equivale al vecchio "giorno precedente/successivo" e, poiché la finestra si ri-centra
    // a ogni selezione, lo scorrimento resta di fatto infinito in entrambe le direzioni.
    // Per i salti arbitrari (date lontane) resta il calendario (onPickDate).
    val days = remember(selectedDate) {
        (-DAY_STRIP_BACK..DAY_STRIP_FORWARD).map { offset ->
            selectedDate.plus(DatePeriod(days = offset))
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedDate) {
        // tiene la data selezionata in vista, mostrando qualche giorno che la precede
        listState.animateScrollToItem((DAY_STRIP_BACK - 2).coerceAtLeast(0))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDate(selectedDate),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onPickDate) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = stringResource(R.string.diary_pick_date)
                )
            }
        }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = days, key = { it.toString() }) { day ->
                DayCell(
                    date = day,
                    selected = day == selectedDate,
                    onClick = { onSelectDate(day) }
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        modifier = Modifier
            .size(width = 56.dp, height = 68.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayShortLabel(date.dayOfWeek),
                style = MaterialTheme.typography.labelSmall,
                color = content
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = content
            )
        }
    }
}

@Composable
private fun DailyTotalsCard(totals: NutrientTotals) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.diary_daily_totals),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(formatTotalsLine(totals), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 6.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = mealTypeLabel(meal.type),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = formatTime(meal.time.hour, meal.time.minute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit, enabled = meal.id != null) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit))
                    }
                    IconButton(onClick = onDelete, enabled = meal.id != null) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            meal.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(4.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            meal.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "• ${item.foodName}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatQuantity(item.originalQuantity, item.originalUnit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = formatTotalsLine(meal.totals),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SymptomCard(
    symptom: Symptom,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 6.dp),
                            tint = severityColor(symptom.severity)
                        )
                        Text(
                            text = symptomDisplayLabel(symptom),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = formatTime(symptom.time.hour, symptom.time.minute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit, enabled = symptom.id != null) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit))
                    }
                    IconButton(onClick = onDelete, enabled = symptom.id != null) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.patient_diary_severity_label, severityLabel(symptom.severity)),
                style = MaterialTheme.typography.bodySmall,
                color = severityColor(symptom.severity),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun severityColor(severity: SymptomSeverity): Color = when (severity) {
    SymptomSeverity.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    SymptomSeverity.MILD -> MaterialTheme.colorScheme.tertiary
    SymptomSeverity.MODERATE -> MaterialTheme.colorScheme.secondary
    SymptomSeverity.SEVERE -> MaterialTheme.colorScheme.error
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectorDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = initialDate
        .atStartOfDayIn(TimeZone.UTC)
        .toEpochMilliseconds()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onDateSelected(epochMillisToDate(it))
                }
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
internal fun mealTypeLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> stringResource(R.string.meal_type_breakfast)
    MealType.LUNCH -> stringResource(R.string.meal_type_lunch)
    MealType.SNACK -> stringResource(R.string.meal_type_snack)
    MealType.DINNER -> stringResource(R.string.meal_type_dinner)
}

internal fun formatDate(date: LocalDate): String {
    val day = date.day.toString().padStart(2, '0')
    val month = (date.month.ordinal + 1).toString().padStart(2, '0')
    return "$day/$month/${date.year}"
}

internal fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

internal fun formatQuantity(quantity: Double, unit: String): String {
    val rounded = if (quantity == quantity.toInt().toDouble()) quantity.toInt().toString()
    else "%.1f".format(quantity)
    return "$rounded $unit"
}

@Composable
internal fun formatTotalsLine(totals: NutrientTotals): String = stringResource(
    R.string.diary_totals_line,
    totals.calories.toInt(),
    "%.1f".format(totals.lactose),
    "%.1f".format(totals.sorbitol),
    "%.1f".format(totals.gluten)
)

private fun epochMillisToDate(millis: Long): LocalDate {
    val instant = Instant.fromEpochMilliseconds(millis)
    return instant.toLocalDateTime(TimeZone.UTC).date
}

@Preview(showBackground = true)
@Composable
private fun DiaryDayStripPreview() {
    NutreaseTheme {
        DiaryDayStrip(
            selectedDate = LocalDate(2026, 5, 25),
            onSelectDate = {},
            onPickDate = {}
        )
    }
}