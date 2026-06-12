package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.toDbDescription
import com.example.nutrease.data.dto.toSymptomType
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.model.SymptomType
import org.junit.Assert.assertEquals
import org.junit.Test

class SymptomRepositoryImplTest {

    @Test
    fun `db description round-trips for known types`() {
        SymptomType.entries.forEach { type ->
            val label = type.toDbDescription()
            val parsed = label.toSymptomType()
            assertEquals(type, parsed)
        }
    }

    @Test
    fun `unknown db description maps to OTHER`() {
        assertEquals(SymptomType.OTHER, "Stringa sconosciuta".toSymptomType())
    }

    @Test
    fun `severity intensity contract is monotonic`() {
        assertEquals(1, SymptomSeverity.NONE.intensity)
        assertEquals(3, SymptomSeverity.MILD.intensity)
        assertEquals(6, SymptomSeverity.MODERATE.intensity)
        assertEquals(9, SymptomSeverity.SEVERE.intensity)
    }

    @Test
    fun `severity fromIntensity buckets values into expected ranges`() {
        assertEquals(SymptomSeverity.NONE, SymptomSeverity.fromIntensity(1))
        assertEquals(SymptomSeverity.NONE, SymptomSeverity.fromIntensity(2))
        assertEquals(SymptomSeverity.MILD, SymptomSeverity.fromIntensity(3))
        assertEquals(SymptomSeverity.MILD, SymptomSeverity.fromIntensity(4))
        assertEquals(SymptomSeverity.MODERATE, SymptomSeverity.fromIntensity(5))
        assertEquals(SymptomSeverity.MODERATE, SymptomSeverity.fromIntensity(7))
        assertEquals(SymptomSeverity.SEVERE, SymptomSeverity.fromIntensity(8))
        assertEquals(SymptomSeverity.SEVERE, SymptomSeverity.fromIntensity(10))
    }
}