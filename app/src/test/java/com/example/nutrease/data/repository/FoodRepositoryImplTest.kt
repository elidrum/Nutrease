package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.FoodDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.domain.model.Food
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodRepositoryImplTest {

    @Test
    fun `toDomain parses numeric unit conversions and skips non-numeric entries`() {
        val dto = FoodDto(
            id = 1,
            name = "Pane",
            unitConversions = buildJsonObject {
                put("fetta", 30.0)
                put("cucchiaio", 15.0)
                put("bad", "x") // valore non numerico: va ignorato
            }
        )

        val food = dto.toDomain()

        assertEquals(30.0, food.unitConversions["fetta"]!!, 0.0001)
        assertEquals(15.0, food.unitConversions["cucchiaio"]!!, 0.0001)
        assertFalse(food.unitConversions.containsKey("bad"))
    }

    @Test
    fun `toDomain with null conversions yields an empty map`() {
        val dto = FoodDto(id = 1, name = "Acqua", unitConversions = null)
        assertTrue(dto.toDomain().unitConversions.isEmpty())
    }

    @Test
    fun `availableUnits prepends grams and gramsFor converts correctly`() {
        val food = Food(
            id = 1, name = "Pane", category = null,
            lactosePer100g = 0.0, sorbitolPer100g = 0.0, glutenPer100g = 5.0,
            caloriesPer100g = 250.0, unitConversions = mapOf("fetta" to 30.0)
        )

        assertEquals(listOf("g", "fetta"), food.availableUnits())
        assertEquals(100.0, food.gramsFor("g", 100.0), 0.0001)
        assertEquals(60.0, food.gramsFor("fetta", 2.0), 0.0001)
        // Unità sconosciuta -> 0 (fallback difensivo)
        assertEquals(0.0, food.gramsFor("sconosciuta", 5.0), 0.0001)
    }
}