package com.example.nutrease.ui.screens.patientstats

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.BmiCategory
import com.example.nutrease.domain.model.BmiResult
import com.example.nutrease.domain.model.DailyNutrientPoint
import com.example.nutrease.domain.model.NutrientFilter
import com.example.nutrease.domain.model.PatientStats
import com.example.nutrease.domain.model.SymptomFrequency
import com.example.nutrease.ui.components.NutrientLineChart
import com.example.nutrease.ui.components.ScrollableChipsRow
import com.example.nutrease.ui.screens.diary.symptomTypeLabel
import kotlinx.datetime.LocalDate

/**
 * Dashboard statistiche del paziente, ultimi 30 giorni (RF21): sezione BMI (per ora
 * placeholder), line chart nutrienti con chip di selezione, top 3 sintomi e % giorni
 * sintomatici con barra di progresso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientStatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PatientStatsViewModel = hiltViewModel()
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
                            stringResource(R.string.patient_stats_title_with_name, uiState.patientName)
                        else
                            stringResource(R.string.patient_stats_title)
                    )
                },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val stats = uiState.stats
            when {
                uiState.isLoading && stats == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                stats != null -> PatientStatsContent(
                    stats = stats,
                    selectedNutrient = uiState.selectedNutrient,
                    onSelectNutrient = viewModel::updateNutrient
                )

                else -> Text(
                    text = stringResource(R.string.patient_stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
        }
    }
}

@Composable
private fun PatientStatsContent(
    stats: PatientStats,
    selectedNutrient: NutrientFilter,
    onSelectNutrient: (NutrientFilter) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { BmiSection(bmi = stats.bmi) }
        item {
            NutrientTrendSection(
                series = stats.dailySeriesByFilter,
                selected = selectedNutrient,
                onSelect = onSelectNutrient
            )
        }
        item { TopSymptomsSection(top = stats.topSymptoms) }
        item {
            SymptomaticDaysSection(
                percent = stats.symptomaticDaysPercent,
                totalDays = stats.totalDays
            )
        }
    }
}

@Composable
private fun BmiSection(bmi: BmiResult?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MonitorWeight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.padding(end = 8.dp))
                Text(
                    text = stringResource(R.string.patient_stats_bmi_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            if (bmi == null) {
                Text(
                    text = stringResource(R.string.patient_stats_bmi_unavailable_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.patient_stats_bmi_unavailable_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "%.1f".format(bmi.value),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = bmiColor(bmi.category)
                )
                Text(
                    text = bmiCategoryLabel(bmi.category),
                    style = MaterialTheme.typography.bodyMedium,
                    color = bmiColor(bmi.category)
                )
            }
        }
    }
}

@Composable
private fun NutrientTrendSection(
    series: Map<NutrientFilter, List<DailyNutrientPoint>>,
    selected: NutrientFilter,
    onSelect: (NutrientFilter) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.patient_stats_trend_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            NutrientChipsExcludeAll(selected = selected, onSelect = onSelect)
            Spacer(Modifier.height(12.dp))
            val points = series[selected].orEmpty()
            NutrientLineChart(
                points = points,
                lineColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            TrendStatsLine(points = points, filter = selected)
        }
    }
}

@Composable
private fun NutrientChipsExcludeAll(
    selected: NutrientFilter,
    onSelect: (NutrientFilter) -> Unit
) {
    // Lista a scorrimento con indicatore (prima "Calorie" finiva tagliato). È dentro una Card:
    // edgeColor = container della Card; contentPadding 0 perché la Column della Card ha già 16dp.
    ScrollableChipsRow(
        edgeColor = CardDefaults.cardColors().containerColor,
        contentPadding = PaddingValues(0.dp)
    ) {
        listOf(
            NutrientFilter.LACTOSE,
            NutrientFilter.SORBITOL,
            NutrientFilter.GLUTEN,
            NutrientFilter.CALORIES
        ).forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(nutrientFilterShortLabel(filter)) }
            )
        }
    }
}

@Composable
private fun nutrientFilterShortLabel(filter: NutrientFilter): String = when (filter) {
    NutrientFilter.LACTOSE -> stringResource(R.string.nutrient_filter_lactose)
    NutrientFilter.SORBITOL -> stringResource(R.string.nutrient_filter_sorbitol)
    NutrientFilter.GLUTEN -> stringResource(R.string.nutrient_filter_gluten)
    NutrientFilter.CALORIES -> stringResource(R.string.nutrient_filter_calories)
    NutrientFilter.ALL -> stringResource(R.string.nutrient_filter_all)
}

@Composable
private fun TrendStatsLine(points: List<DailyNutrientPoint>, filter: NutrientFilter) {
    if (points.isEmpty()) return
    val nonZero = points.filter { it.value > 0.0 }
    val average = if (nonZero.isEmpty()) 0.0 else nonZero.sumOf { it.value } / points.size
    val peak = points.maxByOrNull { it.value }

    val averageText = formatValue(average, filter)
    if (peak == null || peak.value <= 0.0) {
        Text(
            text = stringResource(R.string.patient_stats_trend_average_only, averageText),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            text = stringResource(
                R.string.patient_stats_trend_summary,
                averageText,
                formatValue(peak.value, filter),
                formatDate(peak.date)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TopSymptomsSection(top: List<SymptomFrequency>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.patient_stats_top_symptoms_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (top.isEmpty()) {
                Text(
                    text = stringResource(R.string.patient_stats_top_symptoms_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                top.forEachIndexed { index, item ->
                    SymptomFrequencyRow(rank = index + 1, item = item)
                }
            }
        }
    }
}

@Composable
private fun SymptomFrequencyRow(rank: Int, item: SymptomFrequency) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Text(
            text = symptomTypeLabel(item.type),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.patient_stats_symptom_count, item.count)) },
            enabled = false
        )
    }
}

@Composable
private fun SymptomaticDaysSection(percent: Double, totalDays: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.patient_stats_symptomatic_days_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            val intPercent = percent.toInt()
            val symptomaticCount = ((percent / 100.0) * totalDays).toInt()
            Text(
                text = pluralStringResource(
                    R.plurals.patient_stats_symptomatic_days_value,
                    totalDays,
                    intPercent,
                    symptomaticCount,
                    totalDays
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun bmiColor(category: BmiCategory): Color = when (category) {
    BmiCategory.UNDERWEIGHT -> MaterialTheme.colorScheme.secondary
    BmiCategory.NORMAL -> MaterialTheme.colorScheme.primary
    BmiCategory.OVERWEIGHT -> MaterialTheme.colorScheme.tertiary
    BmiCategory.OBESE -> MaterialTheme.colorScheme.error
}

@Composable
private fun bmiCategoryLabel(category: BmiCategory): String = when (category) {
    BmiCategory.UNDERWEIGHT -> stringResource(R.string.bmi_category_underweight)
    BmiCategory.NORMAL -> stringResource(R.string.bmi_category_normal)
    BmiCategory.OVERWEIGHT -> stringResource(R.string.bmi_category_overweight)
    BmiCategory.OBESE -> stringResource(R.string.bmi_category_obese)
}

private fun formatValue(value: Double, filter: NutrientFilter): String = when (filter) {
    NutrientFilter.CALORIES -> "${value.toInt()} kcal"
    else -> "%.1f g".format(value)
}

private fun formatDate(date: LocalDate): String {
    val day = date.day.toString().padStart(2, '0')
    val month = (date.month.ordinal + 1).toString().padStart(2, '0')
    return "$day/$month"
}
