package com.example.nutrease.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella `alimento_pasto`. I campi `*Calc` hanno default 0.0 perché in
 * scrittura NON si inviano: li calcola il trigger `calcola_nutrienti_pasto` sul DB
 * a partire da `QuantitaGrammi`; in lettura tornano valorizzati.
 */
@Serializable
data class MealFoodDto(
    @SerialName("IdAlimentoPasto") val id: Long? = null,
    @SerialName("IdPasto") val mealId: Long,
    @SerialName("IdAlimento") val foodId: Int,
    @SerialName("QuantitaGrammi") val quantityGrams: Double,
    @SerialName("UnitaMisuraOrig") val originalUnit: String? = null,
    @SerialName("QuantitaOrig") val originalQuantity: Double? = null,
    @SerialName("LattosioCalc") val lactose: Double = 0.0,
    @SerialName("SorbitoloCalc") val sorbitol: Double = 0.0,
    @SerialName("GlutineCalc") val gluten: Double = 0.0,
    @SerialName("CalorieCalc") val calories: Double = 0.0
)