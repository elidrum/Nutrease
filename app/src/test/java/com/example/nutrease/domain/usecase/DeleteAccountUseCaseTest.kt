package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAccountUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val useCase = DeleteAccountUseCase(authRepository)

    private val user = AuthUser("uid", "user@example.com", UserRole.PATIENT, "P1")

    @Test
    fun `valid password reauthenticates, deletes server-side and logs out`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user
        coEvery { authRepository.reauthenticate("user@example.com", "Secret12") } returns Result.success(Unit)
        coEvery { authRepository.deleteAccount() } returns Result.success(Unit)
        coEvery { authRepository.logout() } returns Unit

        val result = useCase("Secret12")

        assertTrue(result.isSuccess)
        coVerify { authRepository.deleteAccount() }
        coVerify { authRepository.logout() }
    }

    @Test
    fun `wrong password aborts before deleting`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user
        coEvery { authRepository.reauthenticate("user@example.com", "wrong") } returns
            Result.failure(DomainError.InvalidCredentials)

        val result = useCase("wrong")

        assertTrue(result.isFailure)
        assertEquals(DomainError.InvalidCredentials, result.exceptionOrNull())
        coVerify(exactly = 0) { authRepository.deleteAccount() }
        coVerify(exactly = 0) { authRepository.logout() }
    }

    @Test
    fun `server refusal surfaces still-linked error without logging out`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user
        coEvery { authRepository.reauthenticate("user@example.com", "Secret12") } returns Result.success(Unit)
        coEvery { authRepository.deleteAccount() } returns Result.failure(DomainError.StillHasLinkedPatients)

        val result = useCase("Secret12")

        assertTrue(result.isFailure)
        assertEquals(DomainError.StillHasLinkedPatients, result.exceptionOrNull())
        coVerify(exactly = 0) { authRepository.logout() }
    }

    @Test
    fun `no current user fails as not authenticated`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        val result = useCase("whatever")

        assertTrue(result.isFailure)
        assertEquals(DomainError.NotAuthenticated, result.exceptionOrNull())
        coVerify(exactly = 0) { authRepository.deleteAccount() }
    }

    @Test
    fun `logout failure after deletion still reports success`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user
        coEvery { authRepository.reauthenticate("user@example.com", "Secret12") } returns Result.success(Unit)
        coEvery { authRepository.deleteAccount() } returns Result.success(Unit)
        // La sessione lato server è già invalidata: signOut può fallire, ma la
        // cancellazione è avvenuta → l'esito complessivo resta success.
        coEvery { authRepository.logout() } throws IllegalStateException("session already gone")

        val result = useCase("Secret12")

        assertTrue(result.isSuccess)
    }
}
