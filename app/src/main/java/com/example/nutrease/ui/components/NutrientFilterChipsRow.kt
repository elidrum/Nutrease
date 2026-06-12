package com.example.nutrease.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.nutrease.R
import com.example.nutrease.domain.model.NutrientFilter

/** Etichetta italiana del filtro nutriente, da `strings.xml`. */
@Composable
fun nutrientFilterLabel(filter: NutrientFilter): String = when (filter) {
    NutrientFilter.ALL -> stringResource(R.string.nutrient_filter_all)
    NutrientFilter.LACTOSE -> stringResource(R.string.nutrient_filter_lactose)
    NutrientFilter.SORBITOL -> stringResource(R.string.nutrient_filter_sorbitol)
    NutrientFilter.GLUTEN -> stringResource(R.string.nutrient_filter_gluten)
    NutrientFilter.CALORIES -> stringResource(R.string.nutrient_filter_calories)
}

/** Riga di chip a selezione singola per il filtro nutriente (RF20), scorrevole con affordance. */
@Composable
fun NutrientFilterChipsRow(
    selected: NutrientFilter,
    onSelect: (NutrientFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    // Già scorrevole; ora con indicatore visivo (sfumatura + chevron) così è chiaro che scorre.
    ScrollableChipsRow(
        modifier = modifier,
        edgeColor = MaterialTheme.colorScheme.background
    ) {
        NutrientFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(nutrientFilterLabel(filter)) }
            )
        }
    }
}