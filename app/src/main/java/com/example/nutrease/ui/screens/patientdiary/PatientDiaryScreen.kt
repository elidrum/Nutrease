package com.example.nutrease.ui.screens.patientdiary

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.model.NutrientFilter
import com.example.nutrease.domain.model.NutrientTotals
import com.example.nutrease.domain.model.PatientDiaryDay
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.ui.components.DateRangeSelector
import com.example.nutrease.ui.components.NutrientFilterChipsRow
import com.example.nutrease.ui.screens.diary.formatQuantity
import com.example.nutrease.ui.screens.diary.mealTypeLabel
import com.example.nutrease.ui.screens.diary.severityLabel
import com.example.nutrease.ui.screens.diary.symptomDisplayLabel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * Diario del paziente in sola lettura, lato specialista (RF19–RF20): filtro periodo
 * (chip + DateRangePicker per il personalizzato), chip nutriente che evidenzia senza
 * filtrare, timeline raggruppata per giorno (header con data italiana). Nessuna azione
 * di modifica: niente FAB né menu. Dall'app bar si apre la dashboard statistiche (RF21).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDiaryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStats: (Int, String?) -> Unit = { _, _ -> },
    viewModel: PatientDiaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = {
                    Text(
                        text = if (uiState.patientName.isNotBlank())
                            stringResource(R.string.patient_diary_title_with_name, uiState.patientName)
                        else
                            stringResource(R.string.patient_diary_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onNavigateToStats(uiState.fascicoloId, uiState.patientName)
                    }) {
                        Icon(
                            Icons.Default.QueryStats,
                            contentDescription = stringResource(R.string.patient_stats_action)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DateRangeSelector(
                selected = uiState.range,
                onSelect = viewModel::updateRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Spacer(Modifier.height(8.dp))

            NutrientFilterChipsRow(
                selected = uiState.nutrientFilter,
                onSelect = viewModel::updateNutrientFilter,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            TotalsBox(
                totals = uiState.periodTotals,
                filter = uiState.nutrientFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && uiState.days.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    uiState.days.all { it.isEmpty } ->
                        Text(
                            text = stringResource(R.string.patient_diary_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )

                    else -> DiaryDaysList(
                        days = uiState.days,
                        nutrientFilter = uiState.nutrientFilter
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalsBox(
    totals: NutrientTotals,
    filter: NutrientFilter,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.patient_diary_totals_period),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (filter == NutrientFilter.ALL) {
                NutrientTotalsRow(label = stringResource(R.string.nutrient_filter_lactose), text = formatGrams(totals.lactose))
                NutrientTotalsRow(label = stringResource(R.string.nutrient_filter_sorbitol), text = formatGrams(totals.sorbitol))
                NutrientTotalsRow(label = stringResource(R.string.nutrient_filter_gluten), text = formatGrams(totals.gluten))
                NutrientTotalsRow(label = stringResource(R.string.nutrient_filter_calories), text = formatCalories(totals.calories))
            } else {
                Text(
                    text = formatFilterValue(filter, totals),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun NutrientTotalsRow(label: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiaryDaysList(
    days: List<PatientDiaryDay>,
    nutrientFilter: NutrientFilter
) {
    val visibleDays = remember(days) { days.filterNot { it.isEmpty } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items = visibleDays, key = { it.date.toString() }) { day ->
            DiaryDaySection(day = day, nutrientFilter = nutrientFilter)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DiaryDaySection(day: PatientDiaryDay, nutrientFilter: NutrientFilter) {
    val entries = remember(day) {
        (day.meals.map { TimelineEntry.MealEntry(it) } +
            day.symptoms.map { TimelineEntry.SymptomEntry(it) })
            .sortedBy { it.time }
    }
    Column {
        DayHeader(date = day.date)
        Spacer(Modifier.height(8.dp))
        entries.forEach { entry ->
            when (entry) {
                is TimelineEntry.MealEntry -> MealCard(meal = entry.meal, nutrientFilter = nutrientFilter)
                is TimelineEntry.SymptomEntry -> SymptomCard(symptom = entry.symptom)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatItalianDayHeader(date),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MealCard(meal: Meal, nutrientFilter: NutrientFilter) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = mealTypeLabel(meal.type),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatLocalTime(meal.time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            meal.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (meal.items.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                meal.items.forEach { item ->
                    Text(
                        text = "• ${item.foodName} — ${formatQuantity(item.originalQuantity, item.originalUnit)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            MealNutrientHighlight(totals = meal.totals, filter = nutrientFilter)
        }
    }
}

@Composable
private fun MealNutrientHighlight(totals: NutrientTotals, filter: NutrientFilter) {
    if (filter == NutrientFilter.ALL) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NutrientPill(label = stringResource(R.string.nutrient_short_lactose), value = formatGrams(totals.lactose))
            NutrientPill(label = stringResource(R.string.nutrient_short_sorbitol), value = formatGrams(totals.sorbitol))
            NutrientPill(label = stringResource(R.string.nutrient_short_gluten), value = formatGrams(totals.gluten))
            NutrientPill(label = stringResource(R.string.nutrient_short_calories), value = formatCalories(totals.calories))
        }
    } else {
        Text(
            text = formatFilterValue(filter, totals),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun NutrientPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SymptomCard(symptom: Symptom) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = symptomDisplayLabel(symptom),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatLocalTime(symptom.time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.patient_diary_severity_label, severityLabel(symptom.severity)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private sealed interface TimelineEntry {
    val time: LocalTime
    data class MealEntry(val meal: Meal) : TimelineEntry {
        override val time: LocalTime get() = meal.time
    }
    data class SymptomEntry(val symptom: Symptom) : TimelineEntry {
        override val time: LocalTime get() = symptom.time
    }
}

private fun formatGrams(value: Double): String = "%.1f g".format(value)

private fun formatCalories(value: Double): String = "${value.toInt()} kcal"

private fun formatFilterValue(filter: NutrientFilter, totals: NutrientTotals): String =
    when (filter) {
        NutrientFilter.ALL,
        NutrientFilter.LACTOSE -> formatGrams(totals.lactose)
        NutrientFilter.SORBITOL -> formatGrams(totals.sorbitol)
        NutrientFilter.GLUTEN -> formatGrams(totals.gluten)
        NutrientFilter.CALORIES -> formatCalories(totals.calories)
    }

private fun formatLocalTime(time: LocalTime): String {
    val h = time.hour.toString().padStart(2, '0')
    val m = time.minute.toString().padStart(2, '0')
    return "$h:$m"
}

private fun formatItalianDayHeader(date: LocalDate): String {
    val dayName = italianDayOfWeek(date.dayOfWeek)
    val monthName = italianMonth(date.month)
    return "$dayName ${date.day} $monthName ${date.year}"
}

private fun italianDayOfWeek(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Lunedì"
    DayOfWeek.TUESDAY -> "Martedì"
    DayOfWeek.WEDNESDAY -> "Mercoledì"
    DayOfWeek.THURSDAY -> "Giovedì"
    DayOfWeek.FRIDAY -> "Venerdì"
    DayOfWeek.SATURDAY -> "Sabato"
    DayOfWeek.SUNDAY -> "Domenica"
}

private fun italianMonth(month: Month): String = when (month) {
    Month.JANUARY -> "gennaio"
    Month.FEBRUARY -> "febbraio"
    Month.MARCH -> "marzo"
    Month.APRIL -> "aprile"
    Month.MAY -> "maggio"
    Month.JUNE -> "giugno"
    Month.JULY -> "luglio"
    Month.AUGUST -> "agosto"
    Month.SEPTEMBER -> "settembre"
    Month.OCTOBER -> "ottobre"
    Month.NOVEMBER -> "novembre"
    Month.DECEMBER -> "dicembre"
}