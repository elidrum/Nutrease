package com.example.nutrease.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BmiTest {

    @Test
    fun `non positive inputs return null`() {
        assertNull(computeBmi(weightKg = 0.0, heightMeters = 1.7))
        assertNull(computeBmi(weightKg = 70.0, heightMeters = 0.0))
        assertNull(computeBmi(weightKg = -1.0, heightMeters = 1.7))
        assertNull(computeBmi(weightKg = 70.0, heightMeters = -0.5))
    }

    @Test
    fun `who underweight boundary below 18 dot 5`() {
        val result = computeBmi(weightKg = 49.0, heightMeters = 1.7)!!
        assertEquals(BmiCategory.UNDERWEIGHT, result.category)
        assertEquals(16.95, result.value, 0.01)
    }

    @Test
    fun `who normal range 18 dot 5 to 24 dot 9`() {
        val lower = computeBmi(weightKg = 53.5, heightMeters = 1.7)!!
        assertEquals(BmiCategory.NORMAL, lower.category)

        val upper = computeBmi(weightKg = 71.5, heightMeters = 1.7)!!
        assertEquals(BmiCategory.NORMAL, upper.category)
    }

    @Test
    fun `who overweight range 25 to 29 dot 9`() {
        val result = computeBmi(weightKg = 80.0, heightMeters = 1.7)!!
        assertEquals(BmiCategory.OVERWEIGHT, result.category)
        assertEquals(27.68, result.value, 0.01)
    }

    @Test
    fun `who obese at and above 30`() {
        val result = computeBmi(weightKg = 90.0, heightMeters = 1.7)!!
        assertEquals(BmiCategory.OBESE, result.category)
        assertEquals(31.14, result.value, 0.01)
    }
}