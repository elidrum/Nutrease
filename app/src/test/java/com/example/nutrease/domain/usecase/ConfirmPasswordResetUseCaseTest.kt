package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmPasswordResetUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val useCase = ConfirmPasswordResetUseCase(authRepository)

    @Test
    fun `valid code and password delegate to repository`() = runTest {
        coEvery { authRepository.confirmPasswordReset("u@x.it", "123456", "NewPass1") } returns Result.success(Unit)

        val result = useCase("u@x.it", "123456", "NewPass1")

        assertTrue(result.isSuccess)
        coVerify { authRepository.confirmPasswordReset("u@x.it", "123456", "NewPass1") }
    }

    @Test
    fun `code is trimmed before delegation`() = runTest {
        coEvery { authRepository.confirmPasswordReset("u@x.it", "123456", "NewPass1") } returns Result.success(Unit)

        val result = useCase("u@x.it", "  123456  ", "NewPass1")

        assertTrue(result.isSuccess)
        coVerify { authRepository.confirmPasswordReset("u@x.it", "123456", "NewPass1") }
    }

    @Test
    fun `non numeric six chars code is rejected before repository`() = runTest {
        val result = useCase("u@x.it", "12345a", "NewPass1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authRepository.confirmPasswordReset(any(), any(), any()) }
    }

    @Test
    fun `wrong length code is rejected before repository`() = runTest {
        val result = useCase("u@x.it", "12345", "NewPass1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authRepository.confirmPasswordReset(any(), any(), any()) }
    }

    @Test
    fun `weak new password is rejected before repository`() = runTest {
        val result = useCase("u@x.it", "123456", "weak")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authRepository.confirmPasswordReset(any(), any(), any()) }
    }
}
