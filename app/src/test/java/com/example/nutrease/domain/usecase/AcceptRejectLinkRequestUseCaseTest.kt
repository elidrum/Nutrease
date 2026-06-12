package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptRejectLinkRequestUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val linkRequestRepository: LinkRequestRepository = mockk()

    private val specialist = AuthUser(
        id = "auth-2",
        email = "s@example.com",
        role = UserRole.SPECIALIST,
        taxCode = "S1"
    )
    private val patient = AuthUser(
        id = "auth-1",
        email = "p@example.com",
        role = UserRole.PATIENT,
        taxCode = "P1"
    )

    @Test
    fun `accept succeeds for specialist`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist
        coEvery { linkRequestRepository.acceptRequest(7L) } returns Result.success(Unit)

        val result = AcceptLinkRequestUseCase(authRepository, linkRequestRepository).invoke(7L)

        assertTrue(result.isSuccess)
        coVerify { linkRequestRepository.acceptRequest(7L) }
    }

    @Test
    fun `accept fails for patient`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns patient

        val result = AcceptLinkRequestUseCase(authRepository, linkRequestRepository).invoke(7L)

        assertTrue(result.isFailure)
    }

    @Test
    fun `reject requires non-blank reason`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist

        val result = RejectLinkRequestUseCase(authRepository, linkRequestRepository)
            .invoke(7L, "  ")

        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `reject succeeds with valid reason`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist
        coEvery { linkRequestRepository.rejectRequest(7L, "Agenda piena") } returns Result.success(Unit)

        val result = RejectLinkRequestUseCase(authRepository, linkRequestRepository)
            .invoke(7L, "Agenda piena")

        assertTrue(result.isSuccess)
        coVerify { linkRequestRepository.rejectRequest(7L, "Agenda piena") }
    }

    @Test
    fun `reject reason longer than 500 fails`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist

        val result = RejectLinkRequestUseCase(authRepository, linkRequestRepository)
            .invoke(7L, "x".repeat(501))

        assertTrue(result.isFailure)
    }
}