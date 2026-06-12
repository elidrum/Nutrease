package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendLinkRequestUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val linkRequestRepository: LinkRequestRepository = mockk()
    private val useCase = SendLinkRequestUseCase(authRepository, linkRequestRepository)

    private val patient = AuthUser(
        id = "auth-1",
        email = "p@example.com",
        role = UserRole.PATIENT,
        taxCode = "P1"
    )
    private val specialist = AuthUser(
        id = "auth-2",
        email = "s@example.com",
        role = UserRole.SPECIALIST,
        taxCode = "S1"
    )

    private fun fakeRequest() = LinkRequest(
        id = 1L,
        patientTaxCode = "P1",
        specialistTaxCode = "S2",
        status = LinkRequestStatus.PENDING,
        message = "Ciao",
        requestedAt = Instant.fromEpochMilliseconds(0),
        respondedAt = null,
        rejectionReason = null
    )

    @Test
    fun `patient can send a request and message is trimmed`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns patient
        coEvery {
            linkRequestRepository.sendRequest("P1", "S2", "Ciao")
        } returns Result.success(fakeRequest())

        val result = useCase("S2", "  Ciao  ")

        assertTrue(result.isSuccess)
        coVerify { linkRequestRepository.sendRequest("P1", "S2", "Ciao") }
    }

    @Test
    fun `blank message becomes null`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns patient
        coEvery {
            linkRequestRepository.sendRequest("P1", "S2", null)
        } returns Result.success(fakeRequest())

        val result = useCase("S2", "   ")

        assertTrue(result.isSuccess)
        coVerify { linkRequestRepository.sendRequest("P1", "S2", null) }
    }

    @Test
    fun `specialist user is rejected`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist

        val result = useCase("S2", null)

        assertTrue(result.isFailure)
        assertEquals(IllegalStateException::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `unauthenticated user is rejected`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        val result = useCase("S2", null)

        assertTrue(result.isFailure)
    }

    @Test
    fun `message exceeding 500 chars is rejected`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns patient

        val result = useCase("S2", "x".repeat(501))

        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
    }
}