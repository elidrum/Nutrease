package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangePasswordUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val useCase = ChangePasswordUseCase(authRepository, userRepository)

    private val user = AuthUser("uid", "user@example.com", UserRole.PATIENT, "P1")

    @Test
    fun `valid change reauthenticates then updates password`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user
        coEvery { authRepository.reauthenticate("user@example.com", "OldPass1") } returns Result.success(Unit)
        coEvery { userRepository.changePassword("NewPass1") } returns Result.success(Unit)

        val result = useCase("OldPass1", "NewPass1")

        assertTrue(result.isSuccess)
        coVerify { authRepository.reauthenticate("user@example.com", "OldPass1") }
        coVerify { userRepository.changePassword("NewPass1") }
    }

    @Test
    fun `weak new password is rejected before any repository call`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user

        val result = useCase("OldPass1", "weak")

        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
        coVerify(exactly = 0) { authRepository.reauthenticate(any(), any()) }
        coVerify(exactly = 0) { userRepository.changePassword(any()) }
    }

    @Test
    fun `new password equal to current is rejected as SamePassword before any repository call`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user

        val result = useCase("SamePass1", "SamePass1")

        assertTrue(result.isFailure)
        assertEquals(DomainError.SamePassword, result.exceptionOrNull())
        coVerify(exactly = 0) { authRepository.reauthenticate(any(), any()) }
        coVerify(exactly = 0) { userRepository.changePassword(any()) }
    }

    @Test
    fun `wrong current password aborts before updating`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user
        coEvery { authRepository.reauthenticate("user@example.com", "WrongPass1") } returns
            Result.failure(IllegalStateException("bad credentials"))

        val result = useCase("WrongPass1", "NewPass1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { userRepository.changePassword(any()) }
    }

    @Test
    fun `unauthenticated user is rejected`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        val result = useCase("OldPass1", "NewPass1")

        assertTrue(result.isFailure)
    }
}