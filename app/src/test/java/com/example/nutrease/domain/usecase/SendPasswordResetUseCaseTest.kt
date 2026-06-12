package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SendPasswordResetUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val useCase = SendPasswordResetUseCase(authRepository)

    @Test
    fun `valid email is trimmed and forwarded`() = runTest {
        coEvery { authRepository.sendPasswordReset("user@example.com") } returns Result.success(Unit)

        val result = useCase("  user@example.com  ")

        assertTrue(result.isSuccess)
        coVerify { authRepository.sendPasswordReset("user@example.com") }
    }

    @Test
    fun `blank email is rejected`() = runTest {
        val result = useCase("   ")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authRepository.sendPasswordReset(any()) }
    }

    @Test
    fun `email without at sign is rejected`() = runTest {
        val result = useCase("not-an-email")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authRepository.sendPasswordReset(any()) }
    }
}