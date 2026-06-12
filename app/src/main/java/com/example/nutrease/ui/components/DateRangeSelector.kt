package com.example.nutrease.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrease.R
import com.example.nutrease.domain.model.DiaryDateRange
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Selettore del periodo (RF20): chip Oggi / 7g / 30g / Personalizzato; quest'ultimo
 * apre il [DateRangePicker] Material3 in dialog e mostra l'intervallo scelto.
 */
// Falso positivo dell'IDE: showCustomPicker è riassegnato (false) nelle callback di dismiss/conferma
// del dialog e riletto alla ricomposizione, non visibile all'analisi statica. La soppressione va a
// livello di funzione (racchiude gli assegnamenti nelle lambda), non sulla dichiarazione del var.
@Suppress("AssignedValueIsNeverRead")
@Composable
fun DateRangeSelector(
    selected: DiaryDateRange,
    onSelect: (DiaryDateRange) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomPicker by remember { mutableStateOf(false) }

    ScrollableChipsRow(
        modifier = modifier,
        edgeColor = MaterialTheme.colorScheme.background
    ) {
        FilterChip(
            selected = selected is DiaryDateRange.Today,
            onClick = { onSelect(DiaryDateRange.Today) },
            label = { Text(stringResource(R.string.date_range_today)) }
        )
        FilterChip(
            selected = selected is DiaryDateRange.Last7Days,
            onClick = { onSelect(DiaryDateRange.Last7Days) },
            label = { Text(stringResource(R.string.date_range_last_7_days)) }
        )
        FilterChip(
            selected = selected is DiaryDateRange.Last30Days,
            onClick = { onSelect(DiaryDateRange.Last30Days) },
            label = { Text(stringResource(R.string.date_range_last_30_days)) }
        )
        FilterChip(
            selected = selected is DiaryDateRange.Custom,
            onClick = { showCustomPicker = true },
            label = { Text(stringResource(R.string.date_range_custom)) }
        )
    }

    if (showCustomPicker) {
        CustomDateRangeDialog(
            initial = selected as? DiaryDateRange.Custom,
            onDismiss = { showCustomPicker = false },
            onConfirm = { start, end ->
                showCustomPicker = false
                onSelect(DiaryDateRange.Custom(start, end))
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDateRangeDialog(
    initial: DiaryDateRange.Custom?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val initialStartMillis = initial?.start?.toUtcEpochMillis()
    val initialEndMillis = initial?.end?.toUtcEpochMillis()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val startMillis = state.selectedStartDateMillis
            val endMillis = state.selectedEndDateMillis
            TextButton(
                enabled = startMillis != null && endMillis != null,
                onClick = {
                    if (startMillis != null && endMillis != null) {
                        onConfirm(epochMillisToDate(startMillis), epochMillisToDate(endMillis))
                    }
                }
            ) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
        colors = DatePickerDefaults.colors()
    ) {
        DateRangePicker(
            state = state,
            // Titolo con più aria in alto (di default era troppo vicino al bordo del dialog).
            title = {
                Text(
                    text = stringResource(R.string.date_range_picker_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)
                )
            },
            // Headline compatta su una riga (la default "Data inizio – Data fine" era troppo grande
            // e andava a capo): mostra il periodo selezionato, o un placeholder breve.
            headline = {
                val startMillis = state.selectedStartDateMillis
                val endMillis = state.selectedEndDateMillis
                val headlineText = when {
                    startMillis != null && endMillis != null ->
                        "${formatHeadlineDate(startMillis)} – ${formatHeadlineDate(endMillis)}"
                    startMillis != null -> formatHeadlineDate(startMillis)
                    else -> stringResource(R.string.date_range_picker_headline_placeholder)
                }
                Text(
                    text = headlineText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                )
            }
        )
    }
}

private fun LocalDate.toUtcEpochMillis(): Long =
    atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun epochMillisToDate(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

/** Data compatta "gg/mm/aaaa" per l'headline del picker (niente a-capo). */
private fun formatHeadlineDate(millis: Long): String {
    val date = epochMillisToDate(millis)
    val day = date.day.toString().padStart(2, '0')
    val month = (date.month.ordinal + 1).toString().padStart(2, '0')
    return "$day/$month/${date.year}"
}

@Preview(showBackground = true)
@Composable
private fun DateRangeSelectorPreview() {
    NutreaseTheme {
        DateRangeSelector(selected = DiaryDateRange.Last7Days, onSelect = {})
    }
}