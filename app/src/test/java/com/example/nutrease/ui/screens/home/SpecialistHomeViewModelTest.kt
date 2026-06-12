package com.example.nutrease.ui.screens.home

import app.cash.turbine.test
import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.LinkRequestWithPatient
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.UserProfile
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.usecase.GetProfileUseCase
import com.example.nutrease.domain.usecase.GetReceivedLinkRequestsUseCase
import com.example.nutrease.domain.usecase.GetUnreadChatsCountUseCase
import com.example.nutrease.domain.usecase.ObserveIncomingMessagesUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpecialistHomeViewModelTest {

    private val getReceivedLinkRequestsUseCase: GetReceivedLinkRequestsUseCase = mockk()
    private val getUnreadChatsCountUseCase: GetUnreadChatsCountUseCase = mockk()
    private val getProfileUseCase: GetProfileUseCase = mockk()
    private val observeIncomingMessagesUseCase: ObserveIncomingMessagesUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private fun specialistProfile(isVerified: Boolean) = UserProfile.SpecialistProfile(
        authUser = AuthUser(id = "uid", email = "s@x.it", role = UserRole.SPECIALIST, taxCode = "S1"),
        specialist = Specialist(
            taxCode = "S1",
            authUid = "uid",
            firstName = "Anna",
            lastName = "Bianchi",
            email = "s@x.it",
            phone = null,
            vatNumber = "12345678901",
            info = null,
            specialization = null,
            city = null,
            isVerified = isVerified
        )
    )

    private fun item(id: Long) = LinkRequestWithPatient(
        request = LinkRequest(
            id = id,
            patientTaxCode = "P$id",
            specialistTaxCode = "S1",
            status = LinkRequestStatus.PENDING,
            message = null,
            requestedAt = Instant.fromEpochMilliseconds(0),
            respondedAt = null,
            rejectionReason = null
        ),
        patientFirstName = "Mario",
        patientLastName = "Rossi$id"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default: nessuna chat non letta (i singoli test lo sovrascrivono se serve).
        coEvery { getUnreadChatsCountUseCase() } returns Result.success(0)
        // Default: specialista verificato → nessun banner "in verifica".
        coEvery { getProfileUseCase() } returns Result.success(specialistProfile(isVerified = true))
        // Nessun messaggio live in arrivo: i badge si ricalcolano solo sulle chiamate esplicite.
        every { observeIncomingMessagesUseCase() } returns emptyFlow()
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init exposes pending requests count`() = runTest {
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returns
            Result.success(listOf(item(1L), item(2L), item(3L)))
        val viewModel = SpecialistHomeViewModel(getReceivedLinkRequestsUseCase, getUnreadChatsCountUseCase, getProfileUseCase, observeIncomingMessagesUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(3, awaitItem().pendingRequestsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshPending decrements count after a request is handled`() = runTest {
        // Simula il ritorno in home dopo l'accettazione di una richiesta:
        // prima 3 pendenti, alla seconda chiamata 2.
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returnsMany listOf(
            Result.success(listOf(item(1L), item(2L), item(3L))),
            Result.success(listOf(item(1L), item(2L)))
        )
        val viewModel = SpecialistHomeViewModel(getReceivedLinkRequestsUseCase, getUnreadChatsCountUseCase, getProfileUseCase, observeIncomingMessagesUseCase)
        advanceUntilIdle()

        viewModel.refreshPending()
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(2, awaitItem().pendingRequestsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure keeps count at zero`() = runTest {
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returns
            Result.failure(Exception("boom"))
        val viewModel = SpecialistHomeViewModel(getReceivedLinkRequestsUseCase, getUnreadChatsCountUseCase, getProfileUseCase, observeIncomingMessagesUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(0, awaitItem().pendingRequestsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init exposes unread chats count and refresh updates it`() = runTest {
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returns
            Result.success(emptyList())
        // prima 2 chat non lette, poi (dopo aver letto) 0
        coEvery { getUnreadChatsCountUseCase() } returnsMany listOf(
            Result.success(2),
            Result.success(0)
        )
        val viewModel = SpecialistHomeViewModel(getReceivedLinkRequestsUseCase, getUnreadChatsCountUseCase, getProfileUseCase, observeIncomingMessagesUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(2, awaitItem().unreadChatsCount)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.refreshUnreadChats()
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(0, awaitItem().unreadChatsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unverified specialist exposes pending verification`() = runTest {
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returns
            Result.success(emptyList())
        coEvery { getProfileUseCase() } returns Result.success(specialistProfile(isVerified = false))
        val viewModel = SpecialistHomeViewModel(getReceivedLinkRequestsUseCase, getUnreadChatsCountUseCase, getProfileUseCase, observeIncomingMessagesUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(true, awaitItem().isPendingVerification)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `verified specialist is not pending`() = runTest {
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returns
            Result.success(emptyList())
        // getProfileUseCase resta sul default (verificato).
        val viewModel = SpecialistHomeViewModel(getReceivedLinkRequestsUseCase, getUnreadChatsCountUseCase, getProfileUseCase, observeIncomingMessagesUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().isPendingVerification)
            cancelAndIgnoreRemainingEvents()
        }
    }
}