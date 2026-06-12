package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.ReminderConfig
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.ReminderRepository
import com.example.nutrease.domain.repository.ReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveReminderConfigUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val reminderRepository: ReminderRepository = mockk()
    private val scheduler: ReminderScheduler = mockk(relaxed = true)
    private val useCase = SaveReminderConfigUseCase(authRepository, reminderRepository, scheduler)

    private val authUser = AuthUser(
        id = "uid-1",
        email = "p@example.com",
        role = UserRole.PATIENT,
        taxCode = "RSSMRA00A01H501Z"
    )

    @Test
    fun `enabled save persists, cancels its own schedule, then reschedules`() = runTest {
        val input = ReminderConfig(
            patientTaxCode = "",
            enabled = true,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            time = LocalTime(7, 30)
        )
        coEvery { authRepository.getCurrentUser() } returns authUser
        val saved = slot<ReminderConfig>()
        coEvery { reminderRepository.saveConfig(capture(saved)) } answers {
            Result.success(saved.captured.copy(id = 99))
        }

        val result = useCase(input)

        assertTrue(result.isSuccess)
        assertEquals(authUser.taxCode, saved.captured.patientTaxCode)
        // riallineamento per il SOLO id, non globale (così altri promemoria restano intatti)
        verify(exactly = 1) { scheduler.cancel(99) }
        verify(exactly = 1) { scheduler.schedule(match { it.id == 99L }) }
        verify(exactly = 0) { scheduler.cancelAll() }
    }

    @Test
    fun `disabled save cancels its own schedule but does not reschedule`() = runTest {
        val input = ReminderConfig(
            id = 5,
            patientTaxCode = "",
            enabled = false,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            time = LocalTime(20, 0)
        )
        coEvery { authRepository.getCurrentUser() } returns authUser
        coEvery { reminderRepository.saveConfig(any()) } answers {
            Result.success(firstArg<ReminderConfig>().copy(id = 5))
        }

        useCase(input)

        verify(exactly = 1) { scheduler.cancel(5) }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `empty days fails before touching repository`() = runTest {
        val input = ReminderConfig(
            patientTaxCode = "",
            enabled = true,
            daysOfWeek = emptySet(),
            time = LocalTime(20, 0)
        )

        val result = useCase(input)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { reminderRepository.saveConfig(any()) }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `unauthenticated user fails`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null
        val input = ReminderConfig(
            patientTaxCode = "",
            enabled = true,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            time = LocalTime(20, 0)
        )

        val result = useCase(input)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { reminderRepository.saveConfig(any()) }
    }
}
