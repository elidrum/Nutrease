package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.ReminderConfig
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.ReminderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetReminderConfigsUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val reminderRepository: ReminderRepository = mockk()
    private val useCase = GetReminderConfigsUseCase(authRepository, reminderRepository)

    private val authUser = AuthUser(
        id = "uid-1",
        email = "p@example.com",
        role = UserRole.PATIENT,
        taxCode = "RSSMRA00A01H501Z"
    )

    private fun config(id: Long, time: LocalTime) = ReminderConfig(
        id = id,
        patientTaxCode = authUser.taxCode,
        enabled = true,
        daysOfWeek = setOf(DayOfWeek.MONDAY),
        time = time
    )

    @Test
    fun `returns reminders sorted by time of day`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns authUser
        coEvery { reminderRepository.getConfigs(authUser.taxCode) } returns Result.success(
            listOf(
                config(1, LocalTime(20, 0)),
                config(2, LocalTime(8, 0)),
                config(3, LocalTime(13, 0))
            )
        )

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(listOf(2L, 3L, 1L), result.getOrThrow().map { it.id })
    }

    @Test
    fun `unauthenticated user fails without hitting repository`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        val result = useCase()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { reminderRepository.getConfigs(any()) }
    }
}
