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
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Test

class RescheduleRemindersUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val reminderRepository: ReminderRepository = mockk()
    private val scheduler: ReminderScheduler = mockk(relaxed = true)
    private val useCase = RescheduleRemindersUseCase(authRepository, reminderRepository, scheduler)

    private fun config(id: Long?, enabled: Boolean) = ReminderConfig(
        id = id,
        patientTaxCode = "CF",
        enabled = enabled,
        daysOfWeek = setOf(DayOfWeek.MONDAY),
        time = LocalTime(8, 0),
        message = null
    )

    @Test
    fun `schedules only enabled configs with id for a patient`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns AuthUser("uid", "p@x.it", UserRole.PATIENT, "CF")
        val enabled = config(1L, true)
        coEvery { reminderRepository.getConfigs("CF") } returns Result.success(
            listOf(enabled, config(2L, false), config(null, true))
        )

        val result = useCase()

        assertTrue(result.isSuccess)
        verify(exactly = 1) { scheduler.schedule(enabled) }
        verify(exactly = 1) { scheduler.schedule(any()) } // disabilitato e senza id esclusi
    }

    @Test
    fun `no-op when not authenticated`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { reminderRepository.getConfigs(any()) }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `no-op for a specialist`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns AuthUser("uid", "s@x.it", UserRole.SPECIALIST, "CFS")

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { reminderRepository.getConfigs(any()) }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }
}
