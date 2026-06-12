package com.example.nutrease.domain.model

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgePolicyTest {

    // "Oggi" fisso per rendere i test deterministici (il dominio riceve la data dall'esterno).
    private val today = LocalDate(2026, 6, 8)

    @Test
    fun `exactly 18 today is allowed`() {
        // compie 18 anni esattamente oggi → ammesso
        val birthDate = LocalDate(2008, 6, 8)
        assertEquals(18, AgePolicy.ageOn(birthDate, today))
        assertNull(AgePolicy.validate(birthDate, today))
    }

    @Test
    fun `eighteen minus one day is rejected`() {
        // compirà 18 anni domani → oggi ne ha 17 → rifiutato
        val birthDate = LocalDate(2008, 6, 9)
        assertEquals(17, AgePolicy.ageOn(birthDate, today))
        assertEquals(AgeValidationError.TOO_YOUNG, AgePolicy.validate(birthDate, today))
    }

    @Test
    fun `one day past 18th birthday is allowed`() {
        val birthDate = LocalDate(2008, 6, 7)
        assertEquals(18, AgePolicy.ageOn(birthDate, today))
        assertNull(AgePolicy.validate(birthDate, today))
    }

    @Test
    fun `clearly adult is allowed`() {
        assertNull(AgePolicy.validate(LocalDate(1990, 1, 1), today))
    }

    @Test
    fun `clearly minor is rejected`() {
        assertEquals(
            AgeValidationError.TOO_YOUNG,
            AgePolicy.validate(LocalDate(2020, 1, 1), today)
        )
    }

    @Test
    fun `leap day birth is handled on non-leap year`() {
        // nato il 29/02/2008: il 28/02/2026 (anno non bisestile) ha ancora 17 anni
        val birthDate = LocalDate(2008, 2, 29)
        assertEquals(17, AgePolicy.ageOn(birthDate, LocalDate(2026, 2, 28)))
        assertEquals(18, AgePolicy.ageOn(birthDate, LocalDate(2026, 3, 1)))
    }
}
