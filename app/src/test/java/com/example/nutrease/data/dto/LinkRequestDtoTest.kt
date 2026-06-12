package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.LinkRequestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LinkRequestDtoTest {

    @Test
    fun `pending dto with no response timestamps maps to PENDING domain`() {
        val dto = LinkRequestDto(
            id = 42L,
            patientTaxCode = "P1",
            specialistTaxCode = "S1",
            status = "In Attesa",
            message = "Vorrei iniziare un percorso",
            requestedAt = "2026-05-01T09:30:00Z",
            respondedAt = null,
            rejectionReason = null
        )
        val domain = dto.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("P1", domain.patientTaxCode)
        assertEquals("S1", domain.specialistTaxCode)
        assertEquals(LinkRequestStatus.PENDING, domain.status)
        assertEquals("Vorrei iniziare un percorso", domain.message)
        assertNull(domain.respondedAt)
        assertNull(domain.rejectionReason)
    }

    @Test
    fun `rejected dto preserves rejection reason and response timestamp`() {
        val dto = LinkRequestDto(
            id = 7L,
            patientTaxCode = "P9",
            specialistTaxCode = "S9",
            status = "Rifiutata",
            requestedAt = "2026-05-01T09:30:00Z",
            respondedAt = "2026-05-02T10:00:00Z",
            rejectionReason = "Agenda completa"
        )
        val domain = dto.toDomain()

        assertEquals(LinkRequestStatus.REJECTED, domain.status)
        assertEquals("Agenda completa", domain.rejectionReason)
    }

    @Test
    fun `nested patient join maps into LinkRequestWithPatient`() {
        val dto = LinkRequestWithPatientDto(
            id = 1L,
            patientTaxCode = "RSSMRA80A01H501Z",
            specialistTaxCode = "LSEMRC80A01H501U",
            status = "In Attesa",
            message = null,
            requestedAt = "2026-05-01T09:30:00Z",
            patient = PatientShortDto(firstName = "Mario", lastName = "Rossi")
        )
        val domain = dto.toDomain()

        assertEquals("Mario", domain.patientFirstName)
        assertEquals("Rossi", domain.patientLastName)
        assertEquals(LinkRequestStatus.PENDING, domain.request.status)
    }

    @Test
    fun `unknown status throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            LinkRequestStatus.fromDbLabel("Boh")
        }
    }
}