package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.SpecialistDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.domain.model.SpecializationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialistDirectoryRepositoryImplTest {

    @Test
    fun `dto with all fields maps cleanly to domain Specialist`() {
        val dto = SpecialistDto(
            taxCode = "RSSMRA80A01H501Z",
            authUid = "uid-1",
            firstName = "Maria",
            lastName = "Rossi",
            phone = "+39 06 1234567",
            email = "maria@example.com",
            vatNumber = "01234567890",
            info = "Esperta in intolleranze alimentari",
            specialization = "Nutrizionista",
            city = "Roma"
        )
        val domain = dto.toDomain()

        assertEquals("RSSMRA80A01H501Z", domain.taxCode)
        assertEquals("uid-1", domain.authUid)
        assertEquals("Maria", domain.firstName)
        assertEquals("Rossi", domain.lastName)
        assertEquals("+39 06 1234567", domain.phone)
        assertEquals("maria@example.com", domain.email)
        assertEquals("01234567890", domain.vatNumber)
        assertEquals("Esperta in intolleranze alimentari", domain.info)
        assertEquals(SpecializationType.NUTRIZIONISTA, domain.specialization)
        assertEquals("Roma", domain.city)
    }

    @Test
    fun `null authUid and email become empty strings in domain`() {
        val dto = SpecialistDto(
            taxCode = "BNCLGI70B02H501T",
            authUid = null,
            firstName = "Luigi",
            lastName = "Bianchi",
            phone = null,
            email = null,
            vatNumber = "99999999999",
            info = null
        )
        val domain = dto.toDomain()

        assertEquals("", domain.authUid)
        assertEquals("", domain.email)
        assertNull(domain.phone)
        assertNull(domain.info)
        assertNull(domain.specialization)
        assertNull(domain.city)
    }

    @Test
    fun `unknown specialization label decodes to null`() {
        val dto = SpecialistDto(
            taxCode = "TSTMRA80A01H501Z",
            authUid = "uid-x",
            firstName = "Test",
            lastName = "Unknown",
            phone = null,
            email = null,
            vatNumber = "11111111111",
            info = null,
            specialization = "NonEsiste",
            city = null
        )
        assertNull(dto.toDomain().specialization)
    }
}