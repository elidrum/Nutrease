package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.BadgeStateRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GetUnseenAcceptedRequestsCountUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val linkRequestRepository: LinkRequestRepository = mockk()
    private val badgeStateRepository: BadgeStateRepository = mockk()
    private val useCase = GetUnseenAcceptedRequestsCountUseCase(
        authRepository, linkRequestRepository, badgeStateRepository
    )

    private fun request(id: Long, status: LinkRequestStatus) = LinkRequest(
        id = id,
        patientTaxCode = "P1",
        specialistTaxCode = "S$id",
        status = status,
        message = null,
        requestedAt = Instant.fromEpochMilliseconds(0),
        respondedAt = null,
        rejectionReason = null
    )

    @Test
    fun `counts accepted requests not yet seen`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns
            AuthUser(id = "u", email = "p@x.it", role = UserRole.PATIENT, taxCode = "P1")
        coEvery { linkRequestRepository.getSentRequests("P1") } returns Result.success(
            listOf(
                request(1, LinkRequestStatus.ACCEPTED),
                request(2, LinkRequestStatus.ACCEPTED),
                request(3, LinkRequestStatus.ACCEPTED),
                request(4, LinkRequestStatus.PENDING),   // ignorata
                request(5, LinkRequestStatus.REJECTED)    // ignorata
            )
        )
        coEvery { badgeStateRepository.seenAcceptedRequestIds() } returns setOf(1L, 2L)

        // accettate {1,2,3} - viste {1,2} = {3} → 1
        assertEquals(1, useCase().getOrThrow())
    }

    @Test
    fun `zero when all accepted requests already seen`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns
            AuthUser(id = "u", email = "p@x.it", role = UserRole.PATIENT, taxCode = "P1")
        coEvery { linkRequestRepository.getSentRequests("P1") } returns
            Result.success(listOf(request(1, LinkRequestStatus.ACCEPTED)))
        coEvery { badgeStateRepository.seenAcceptedRequestIds() } returns setOf(1L)

        assertEquals(0, useCase().getOrThrow())
    }

    @Test
    fun `non patient role returns zero without querying requests`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns
            AuthUser(id = "u", email = "s@x.it", role = UserRole.SPECIALIST, taxCode = "S1")

        assertEquals(0, useCase().getOrThrow())
        coVerify(exactly = 0) { linkRequestRepository.getSentRequests(any()) }
    }

    @Test
    fun `no authenticated user returns zero`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        assertEquals(0, useCase().getOrThrow())
    }
}
