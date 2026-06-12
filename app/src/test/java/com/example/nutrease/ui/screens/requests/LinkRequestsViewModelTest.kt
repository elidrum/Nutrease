package com.example.nutrease.ui.screens.requests

import app.cash.turbine.test
import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.LinkRequestWithPatient
import com.example.nutrease.domain.usecase.AcceptLinkRequestUseCase
import com.example.nutrease.domain.usecase.GetReceivedLinkRequestsUseCase
import com.example.nutrease.domain.usecase.RejectLinkRequestUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkRequestsViewModelTest {

    private val getReceivedLinkRequestsUseCase: GetReceivedLinkRequestsUseCase = mockk()
    private val acceptLinkRequestUseCase: AcceptLinkRequestUseCase = mockk()
    private val rejectLinkRequestUseCase: RejectLinkRequestUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

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
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init loads pending requests`() = runTest {
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returns
            Result.success(listOf(item(1L), item(2L)))

        val vm = LinkRequestsViewModel(
            getReceivedLinkRequestsUseCase,
            acceptLinkRequestUseCase,
            rejectLinkRequestUseCase
        )
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.requests.size)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `accept removes request and emits accepted toast`() = runTest {
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returns
            Result.success(listOf(item(1L), item(2L)))
        coEvery { acceptLinkRequestUseCase(1L) } returns Result.success(Unit)

        val vm = LinkRequestsViewModel(
            getReceivedLinkRequestsUseCase,
            acceptLinkRequestUseCase,
            rejectLinkRequestUseCase
        )
        advanceUntilIdle()

        vm.accept(item(1L))
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.requests.size)
            assertEquals(2L, state.requests.first().request.id)
            assertEquals(LinkRequestsUiState.ToastMessage.ACCEPTED, state.toastMessage)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { acceptLinkRequestUseCase(1L) }
    }

    @Test
    fun `confirmReject with blank reason is no-op`() = runTest {
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returns
            Result.success(listOf(item(1L)))

        val vm = LinkRequestsViewModel(
            getReceivedLinkRequestsUseCase,
            acceptLinkRequestUseCase,
            rejectLinkRequestUseCase
        )
        advanceUntilIdle()

        vm.startReject(item(1L))
        vm.confirmReject("   ")
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.requests.size)
            assertTrue(state.toastMessage == null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmReject removes request and emits rejected toast`() = runTest {
        coEvery { getReceivedLinkRequestsUseCase(LinkRequestStatus.PENDING) } returns
            Result.success(listOf(item(1L)))
        coEvery { rejectLinkRequestUseCase(1L, "Motivo") } returns Result.success(Unit)

        val vm = LinkRequestsViewModel(
            getReceivedLinkRequestsUseCase,
            acceptLinkRequestUseCase,
            rejectLinkRequestUseCase
        )
        advanceUntilIdle()

        vm.startReject(item(1L))
        vm.confirmReject("Motivo")
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.requests.isEmpty())
            assertEquals(LinkRequestsUiState.ToastMessage.REJECTED, state.toastMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }
}