package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.MealType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella `pasto` (solo testata: le righe alimento sono in [MealFoodDto]).
 * [id] nullo con default = omesso nell'insert, lo genera il DB. Sotto, i mapper tra
 * [MealType] e le etichette italiane dell'enum Postgres `Tipologia`.
 */
@Serializable
data class MealDto(
    @SerialName("IdPasto") val id: Long? = null,
    @SerialName("IdFascicolo") val fascicoloId: Int,
    @SerialName("Data") val date: String,
    @SerialName("Ora") val time: String,
    @SerialName("Tipologia") val type: String,
    @SerialName("Descrizione") val description: String? = null
)

fun MealType.toDbValue(): String = when (this) {
    MealType.BREAKFAST -> "Colazione"
    MealType.LUNCH -> "Pranzo"
    MealType.SNACK -> "Merenda"
    MealType.DINNER -> "Cena"
}

fun String.toMealType(): MealType = when (this) {
    "Colazione" -> MealType.BREAKFAST
    "Pranzo" -> MealType.LUNCH
    "Merenda" -> MealType.SNACK
    "Cena" -> MealType.DINNER
    else -> throw IllegalArgumentException("Tipologia pasto sconosciuta: $this")
}