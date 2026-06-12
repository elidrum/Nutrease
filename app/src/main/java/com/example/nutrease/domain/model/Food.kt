package com.example.nutrease.domain.model

/**
 * Alimento del dataset nutrizionale (tabella `alimento`, valori per 100g).
 * [unitConversions] contiene solo le unità non banali (es. "cucchiaio" → 15g):
 * il grammo è l'unità base implicita e non compare nella mappa, per questo
 * [availableUnits] lo antepone come prima opzione canonica.
 */
data class Food(
    val id: Int,
    val name: String,
    val category: String?,
    val lactosePer100g: Double,
    val sorbitolPer100g: Double,
    val glutenPer100g: Double,
    val caloriesPer100g: Double,
    val unitConversions: Map<String, Double>
) {
    fun availableUnits(): List<String> = listOf(UNIT_GRAMS) + unitConversions.keys.toList()

    /** Converte (unità, quantità) in grammi: è la base su cui il DB calcola i nutrienti (RF8). */
    fun gramsFor(unit: String, quantity: Double): Double =
        if (unit == UNIT_GRAMS) quantity
        else (unitConversions[unit] ?: 0.0) * quantity

    companion object {
        const val UNIT_GRAMS = "g"
    }
}