package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.ReminderRepository
import com.example.nutrease.domain.repository.ReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteReminderUseCaseTest {

    private val reminderRepository: ReminderRepository = mockk()
    private val scheduler: ReminderScheduler = mockk(relaxed = true)
    private val useCase = DeleteReminderUseCase(reminderRepository, scheduler)

    @Test
    fun `deletes the row and cancels its schedule`() = runTest {
        coEvery { reminderRepository.deleteConfig(7) } returns Result.success(Unit)

        val result = useCase(7)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { reminderRepository.deleteConfig(7) }
        verify(exactly = 1) { scheduler.cancel(7) }
    }

    @Test
    fun `does not cancel schedule when delete fails`() = runTest {
        coEvery { reminderRepository.deleteConfig(7) } returns Result.failure(RuntimeException("boom"))

        val result = useCase(7)

        assertTrue(result.isFailure)
        verify(exactly = 0) { scheduler.cancel(any()) }
    }
}
