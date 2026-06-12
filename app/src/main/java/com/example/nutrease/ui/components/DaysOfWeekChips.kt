package com.example.nutrease.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrease.R
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.datetime.DayOfWeek

/**
 * Riga di FilterChip per i 7 giorni della settimana (Lun..Dom).
 *
 * Usa [FlowRow] e non uno scorrimento orizzontale: con 7 chip su schermi stretti (o dentro
 * un dialog) i chip che non entrano vanno a capo invece di sparire oltre i bordi, così tutti
 * i giorni — Domenica compresa — restano sempre visibili e selezionabili senza dover scorrere.
 */
@Composable
fun DaysOfWeekChips(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                enabled = enabled,
                label = { Text(dayShortLabel(day)) }
            )
        }
    }
}

/** Etichetta breve italiana di un giorno (Lun..Dom), riusabile fuori dai chip. */
@Composable
fun dayShortLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(R.string.day_short_monday)
    DayOfWeek.TUESDAY -> stringResource(R.string.day_short_tuesday)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.day_short_wednesday)
    DayOfWeek.THURSDAY -> stringResource(R.string.day_short_thursday)
    DayOfWeek.FRIDAY -> stringResource(R.string.day_short_friday)
    DayOfWeek.SATURDAY -> stringResource(R.string.day_short_saturday)
    DayOfWeek.SUNDAY -> stringResource(R.string.day_short_sunday)
}

/** Riepilogo dei giorni selezionati: "Tutti i giorni" oppure "Lun, Mer, Ven" in ordine ISO. */
@Composable
fun daysSummaryLabel(days: Set<DayOfWeek>): String {
    if (days.size == DayOfWeek.entries.size) {
        return stringResource(R.string.reminder_every_day)
    }
    // forEach è inline → le chiamate @Composable (stringResource) sono ammesse qui;
    // joinToString con transform non lo è, quindi raccolgo prima le etichette.
    val labels = mutableListOf<String>()
    DayOfWeek.entries.forEach { day ->
        if (day in days) labels.add(dayShortLabel(day))
    }
    return labels.joinToString(", ")
}

@Preview(showBackground = true)
@Composable
private fun DaysOfWeekChipsPreview() {
    NutreaseTheme {
        DaysOfWeekChips(
            selected = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            onToggle = {}
        )
    }
}
