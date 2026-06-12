package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.ReminderConfig
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderConfigDtoTest {

    @Test
    fun `toDomain maps integer array to DayOfWeek set using ISO numbering`() {
        val dto = ReminderConfigDto(
            id = 1,
            patientTaxCode = "ABC",
            active = true,
            daysOfWeek = listOf(1, 3, 7),
            time = "08:30:00",
            message = "Buongiorno"
        )

        val domain = dto.toDomain()

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY), domain.daysOfWeek)
        assertEquals(LocalTime(8, 30), domain.time)
        assertEquals(true, domain.enabled)
        assertEquals("ABC", domain.patientTaxCode)
        assertEquals("Buongiorno", domain.message)
    }

    @Test
    fun `toDomain ignores out-of-range integers`() {
        val dto = ReminderConfigDto(
            patientTaxCode = "X",
            active = false,
            daysOfWeek = listOf(0, 1, 8, 5),
            time = "20:00:00"
        )

        val domain = dto.toDomain()

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), domain.daysOfWeek)
    }

    @Test
    fun `toDomain tolerates microseconds in time string`() {
        val dto = ReminderConfigDto(
            patientTaxCode = "X",
            active = true,
            daysOfWeek = listOf(1),
            time = "07:15:00.123456"
        )

        assertEquals(LocalTime(7, 15), dto.toDomain().time)
    }

    @Test
    fun `all seven ISO numbers map to the full week including Sunday`() {
        val dto = ReminderConfigDto(
            patientTaxCode = "X",
            active = true,
            daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
            time = "20:00:00"
        )

        assertEquals(DayOfWeek.entries.toSet(), dto.toDomain().daysOfWeek)
        // 7 deve essere proprio la domenica (bug "domenica mancante")
        assertEquals(true, DayOfWeek.SUNDAY in dto.toDomain().daysOfWeek)
    }

    @Test
    fun `domain to dto to domain round-trips every day of the week`() {
        val domain = ReminderConfig(
            patientTaxCode = "X",
            enabled = true,
            daysOfWeek = DayOfWeek.entries.toSet(),
            time = LocalTime(8, 0),
            message = "ciao"
        )

        val roundTripped = domain.toDto().toDomain()

        assertEquals(domain.daysOfWeek, roundTripped.daysOfWeek)
        assertEquals(domain.time, roundTripped.time)
        assertEquals(domain.message, roundTripped.message)
    }

    @Test
    fun `toDto serialises domain as sorted ISO day numbers and HH-mm-ss`() {
        val domain = ReminderConfig(
            patientTaxCode = "X",
            enabled = true,
            daysOfWeek = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            time = LocalTime(7, 5),
            message = null
        )

        val dto = domain.toDto()

        assertEquals(listOf(1, 5, 7), dto.daysOfWeek)
        assertEquals("07:05:00", dto.time)
        assertEquals(true, dto.active)
        assertEquals("X", dto.patientTaxCode)
        assertTrue(dto.message == null)
    }
}