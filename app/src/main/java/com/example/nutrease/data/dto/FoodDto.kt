package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.Food
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Riga della tabella `alimento`. Le conversioni unità arrivano come JSONB libero
 * (es. `{"cucchiaio":15,"fetta":30}`): si tengono come [JsonElement] e si appiattiscono
 * in `Map<String, Double>` nel mapper, ignorando eventuali valori non numerici.
 */
@Serializable
data class FoodDto(
    @SerialName("IdAlimento") val id: Int = 0,
    @SerialName("Nome") val name: String,
    @SerialName("Categoria") val category: String? = null,
    @SerialName("LattosioP100g") val lactosePer100g: Double = 0.0,
    @SerialName("SorbitoloP100g") val sorbitolPer100g: Double = 0.0,
    @SerialName("GlutineP100g") val glutenPer100g: Double = 0.0,
    @SerialName("CaloriePer100g") val caloriesPer100g: Double = 0.0,
    @SerialName("ConversioniUnitaMisura") val unitConversions: JsonElement? = null
)

fun FoodDto.toDomain(): Food = Food(
    id = id,
    name = name,
    category = category,
    lactosePer100g = lactosePer100g,
    sorbitolPer100g = sorbitolPer100g,
    glutenPer100g = glutenPer100g,
    caloriesPer100g = caloriesPer100g,
    unitConversions = unitConversions.toUnitMap()
)

private fun JsonElement?.toUnitMap(): Map<String, Double> {
    val obj = this as? JsonObject ?: return emptyMap()
    return obj.mapNotNull { (key, value) ->
        val number = (value as? JsonPrimitive)?.doubleOrNull
        if (number != null) key to number else null
    }.toMap()
}