package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.data.dto.toDto
import com.example.nutrease.domain.model.Gender
import com.example.nutrease.domain.model.Patient
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class UserRepositoryImplTest {

    private val patient = Patient(
        taxCode = "RSSMRA80A01H501U",
        authUid = "uid-1",
        firstName = "Mario",
        lastName = "Rossi",
        email = "mario@example.com",
        phone = "3331234567",
        gender = Gender.OTHER,
        birthDate = LocalDate(1980, 1, 1),
        street = "Via Roma 1",
        city = "Ancona",
        postalCode = "60100"
    )

    @Test
    fun `patient survives a dto round-trip`() {
        assertEquals(patient, patient.toDto().toDomain())
    }

    @Test
    fun `gender maps across the db boundary in both directions`() {
        assertEquals(Gender.M, patient.copy(gender = Gender.M).toDto().toDomain().gender)
        assertEquals(Gender.F, patient.copy(gender = Gender.F).toDto().toDomain().gender)
        assertEquals(Gender.OTHER, patient.copy(gender = Gender.OTHER).toDto().toDomain().gender)
    }
}