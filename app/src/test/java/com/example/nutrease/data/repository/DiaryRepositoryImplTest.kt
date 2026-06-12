package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.toDbValue
import com.example.nutrease.data.dto.toMealType
import com.example.nutrease.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryRepositoryImplTest {

    @Test
    fun `meal type round-trips through the db value`() {
        MealType.entries.forEach { type ->
            assertEquals(type, type.toDbValue().toMealType())
        }
    }

    @Test
    fun `db values are the expected italian labels`() {
        assertEquals("Colazione", MealType.BREAKFAST.toDbValue())
        assertEquals("Pranzo", MealType.LUNCH.toDbValue())
        assertEquals("Merenda", MealType.SNACK.toDbValue())
        assertEquals("Cena", MealType.DINNER.toDbValue())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown meal type label throws`() {
        "Brunch".toMealType()
    }
}