package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.LinkedPatientDto
import com.example.nutrease.data.dto.LinkedPatientProfileDto
import com.example.nutrease.data.dto.toDomain
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkedPatientsRepositoryImplTest {

    private fun dto(birthDate: String?) = LinkedPatientDto(
        fascicoloId = 7,
        patientTaxCode = "CF1",
        patient = LinkedPatientProfileDto(
            taxCode = "CF1",
            firstName = "Mario",
            lastName = "Rossi",
            email = "mario@example.com",
            birthDate = birthDate
        )
    )

    @Test
    fun `maps embed fields and parses a valid birth date`() {
        val patient = dto("1990-04-15").toDomain()

        assertEquals(7, patient.fascicoloId)
        assertEquals("CF1", patient.taxCode)
        assertEquals("Mario", patient.firstName)
        assertEquals("Rossi", patient.lastName)
        assertEquals("mario@example.com", patient.email)
        assertEquals(LocalDate(1990, 4, 15), patient.birthDate)
    }

    @Test
    fun `null or invalid birth date maps to null`() {
        assertNull(dto(null).toDomain().birthDate)
        assertNull(dto("not-a-date").toDomain().birthDate)
    }
}