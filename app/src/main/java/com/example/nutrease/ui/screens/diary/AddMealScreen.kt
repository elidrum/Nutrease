package com.example.nutrease.ui.screens.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.Food
import com.example.nutrease.domain.model.MealType
import com.example.nutrease.ui.components.ScrollableChipsRow
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Form pasto (RF8/RF9/RF12): ricerca alimenti con risultati live, lista delle righe
 * aggiunte con totali, selezione categoria/data/ora. [initialDate] arriva dal giorno
 * aperto nel diario; in modifica i campi sono precaricati dal ViewModel.
 */
@Composable
fun AddMealScreen(
    initialDate: LocalDate?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddMealViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialDate) {
        if (initialDate != null) viewModel.setInitialDate(initialDate)
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    AddMealContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onDateSelected = viewModel::updateDate,
        onTimeSelected = viewModel::updateTime,
        onTypeSelected = viewModel::updateType,
        onDescriptionChange = viewModel::updateDescription,
        onQueryChange = viewModel::updateQuery,
        onAddItem = viewModel::addItem,
        onRemoveItem = viewModel::removeItem,
        onSave = viewModel::save,
        onErrorShown = viewModel::clearError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
// Falso positivo dell'IDE: gli stati dei dialog (showDatePicker, showTimePicker, foodPicker)
// sono riassegnati nelle callback di dismiss/conferma e riletti alla ricomposizione.
@Suppress("AssignedValueIsNeverRead")
@Composable
private fun AddMealContent(
    uiState: AddMealUiState,
    onNavigateBack: () -> Unit = {},
    onDateSelected: (LocalDate) -> Unit = {},
    onTimeSelected: (LocalTime) -> Unit = {},
    onTypeSelected: (MealType) -> Unit = {},
    onDescriptionChange: (String) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onAddItem: (food: Food, unit: String, quantity: Double) -> Unit = { _, _, _ -> },
    onRemoveItem: (Int) -> Unit = {},
    onSave: () -> Unit = {},
    onErrorShown: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var foodPicker by remember { mutableStateOf<Food?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(if (uiState.isEditing) R.string.add_meal_title_edit else R.string.add_meal_title_new)) },
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
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DateTimeRow(
                date = uiState.date,
                time = uiState.time,
                onPickDate = { showDatePicker = true },
                onPickTime = { showTimePicker = true }
            )

            Text(
                stringResource(R.string.add_meal_type_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            MealTypeSelector(
                selected = uiState.type,
                onSelected = onTypeSelected
            )

            Text(
                stringResource(R.string.add_meal_foods_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FoodSearchSection(
                query = uiState.searchQuery,
                results = uiState.searchResults,
                isSearching = uiState.isSearching,
                onQueryChange = onQueryChange,
                onPickFood = { foodPicker = it }
            )

            if (uiState.items.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        uiState.items.forEachIndexed { index, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.food.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatQuantity(item.originalQuantity, item.originalUnit),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onRemoveItem(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_remove))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.add_meal_description)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(Modifier.heightIn(min = 8.dp))

            if (uiState.isSaving) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Button(
                    onClick = onSave,
                    enabled = uiState.items.isNotEmpty() && uiState.type != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(if (uiState.isEditing) R.string.add_meal_update else R.string.add_meal_save))
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initialDate = uiState.date,
            onDateSelected = {
                onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        TimePickerSheet(
            initialTime = uiState.time,
            onTimeSelected = {
                onTimeSelected(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    foodPicker?.let { food ->
        QuantityDialog(
            food = food,
            onConfirm = { unit, qty ->
                onAddItem(food, unit, qty)
                foodPicker = null
            },
            onDismiss = { foodPicker = null }
        )
    }
}

@Composable
private fun DateTimeRow(
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
                Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.add_meal_pick_date))
            },
            colors = pickerFieldColors(),
            modifier = Modifier
                .weight(1f)
                .clickable { onPickDate() }
        )
        OutlinedTextField(
            value = formatTime(time.hour, time.minute),
            onValueChange = {},
            label = { Text(stringResource(R.string.common_time)) },
            readOnly = true,
            enabled = false,
            trailingIcon = {
                Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.add_meal_pick_time))
            },
            colors = pickerFieldColors(),
            modifier = Modifier
                .weight(1f)
                .clickable { onPickTime() }
        )
    }
}

/**
 * Colori per i campi "picker" (data/ora): restano `enabled = false` (così il tap raggiunge il
 * modifier .clickable e apre il dialog), ma mappiamo i colori disabilitati su quelli attivi per
 * non farli sembrare non modificabili (erano di un grigio troppo chiaro).
 */
@Composable
internal fun pickerFieldColors() = OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun MealTypeSelector(
    selected: MealType?,
    onSelected: (MealType) -> Unit
) {
    // Lista a scorrimento orizzontale con indicatore (i chip non vanno più a capo): edgeColor =
    // background dello Scaffold; contentPadding 0 perché la Column genitore ha già 16dp.
    ScrollableChipsRow(
        modifier = Modifier.fillMaxWidth(),
        edgeColor = MaterialTheme.colorScheme.background,
        contentPadding = PaddingValues(0.dp)
    ) {
        MealType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelected(type) },
                label = { Text(mealTypeLabel(type)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSearchSection(
    query: String,
    results: List<Food>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onPickFood: (Food) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.add_meal_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (isSearching) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
        )

        if (results.isNotEmpty()) {
            Card {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(results, key = { it.id }) { food ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickFood(food) }
                                .padding(12.dp)
                        ) {
                            Text(food.name, style = MaterialTheme.typography.bodyMedium)
                            food.category?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else if (query.isNotBlank() && !isSearching) {
            Text(
                stringResource(R.string.add_meal_no_results),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuantityDialog(
    food: Food,
    onConfirm: (unit: String, quantity: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val units = remember(food) { food.availableUnits() }
    var selectedUnit by remember { mutableStateOf(units.first()) }
    var quantityText by remember { mutableStateOf("") }

    val parsedQty = quantityText.replace(',', '.').toDoubleOrNull() ?: 0.0

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(food.name, style = MaterialTheme.typography.titleMedium)

                Text(
                    stringResource(R.string.add_meal_unit_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    units.forEach { unit ->
                        FilterChip(
                            selected = unit == selectedUnit,
                            onClick = { selectedUnit = unit },
                            label = { Text(unit) }
                        )
                    }
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(stringResource(R.string.add_meal_quantity)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.padding(end = 8.dp))
                    Button(
                        onClick = { onConfirm(selectedUnit, parsedQty) },
                        enabled = parsedQty > 0
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.padding(end = 4.dp))
                        Text(stringResource(R.string.common_add))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
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
                    TextButton(onClick = {
                        onTimeSelected(LocalTime(state.hour, state.minute))
                    }) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddMealContentPreview() {
    val sampleFood = Food(
        id = 1,
        name = "Pane integrale",
        category = "Cereali",
        lactosePer100g = 0.0,
        sorbitolPer100g = 0.0,
        glutenPer100g = 8.0,
        caloriesPer100g = 250.0,
        unitConversions = mapOf("fetta" to 30.0)
    )
    NutreaseTheme {
        AddMealContent(
            uiState = AddMealUiState(
                type = MealType.LUNCH,
                description = "Pranzo leggero",
                items = listOf(
                    PendingMealItem(sampleFood, "fetta", 2.0)
                )
            )
        )
    }
}
