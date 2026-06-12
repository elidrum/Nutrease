package com.example.nutrease.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Riga di dettaglio di un pasto (tabella `alimento_pasto`): un alimento con la quantità
 * scelta. Si conservano sia i grammi calcolati ([quantityGrams], usati dal DB per i
 * nutrienti) sia unità e quantità originali, così in modifica l'utente rivede ciò che
 * aveva digitato ("2 fette", non "60g"). [id] nullo = riga non ancora salvata.
 */
data class MealFoodItem(
    val id: Long?,
    val foodId: Int,
    val foodName: String,
    val quantityGrams: Double,
    val originalUnit: String,
    val originalQuantity: Double,
    val nutrients: NutrientTotals
)

/**
 * Pasto del diario (tabella `pasto`): data+ora, categoria e righe alimento.
 * [totals] somma i nutrienti delle righe on-demand: i valori per riga li calcola
 * il DB all'insert, qui si aggregano solo per la visualizzazione.
 */
data class Meal(
    val id: Long?,
    val fascicoloId: Int,
    val date: LocalDate,
    val time: LocalTime,
    val type: MealType,
    val description: String?,
    val items: List<MealFoodItem>
) {
    val totals: NutrientTotals
        get() = items.fold(NutrientTotals.Zero) { acc, item -> acc + item.nutrients }
}