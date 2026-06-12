package com.example.nutrease.ui.screens.home

import app.cash.turbine.test
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.usecase.GetLinkedSpecialistUseCase
import com.example.nutrease.domain.usecase.GetUnreadChatsCountUseCase
import com.example.nutrease.domain.usecase.GetUnseenAcceptedRequestsCountUseCase
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientHomeViewModelTest {

    private val getUnreadChatsCountUseCase: GetUnreadChatsCountUseCase = mockk()
    private val getUnseenAcceptedRequestsCountUseCase: GetUnseenAcceptedRequestsCountUseCase = mockk()
    private val getLinkedSpecialistUseCase: GetLinkedSpecialistUseCase = mockk()
    private val observeIncomingMessagesUseCase: ObserveIncomingMessagesUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private val linkedSpecialist = Specialist(
        taxCode = "S1",
        authUid = "uid-S1",
        firstName = "Marco",
        lastName = "Bianchi",
        email = "marco@nutrease.it",
        phone = null,
        vatNumber = "11111111111",
        info = null,
        specialization = null,
        city = "Milano"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Nessun messaggio live in arrivo: i badge si ricalcolano solo sulle chiamate esplicite.
        every { observeIncomingMessagesUseCase() } returns emptyFlow()
        coEvery { getLinkedSpecialistUseCase() } returns Result.success(null)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init exposes chat and accepted-request badge counts`() = runTest {
        coEvery { getUnreadChatsCountUseCase() } returns Result.success(2)
        coEvery { getUnseenAcceptedRequestsCountUseCase() } returns Result.success(1)

        val viewModel = PatientHomeViewModel(
            getUnreadChatsCountUseCase,
            getUnseenAcceptedRequestsCountUseCase,
            getLinkedSpecialistUseCase,
            observeIncomingMessagesUseCase
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.unreadChatsCount)
            assertEquals(1, state.acceptedRequestsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh clears badges once everything has been seen`() = runTest {
        coEvery { getUnreadChatsCountUseCase() } returnsMany listOf(
            Result.success(3), Result.success(0)
        )
        coEvery { getUnseenAcceptedRequestsCountUseCase() } returnsMany listOf(
            Result.success(1), Result.success(0)
        )

        val viewModel = PatientHomeViewModel(
            getUnreadChatsCountUseCase,
            getUnseenAcceptedRequestsCountUseCase,
            getLinkedSpecialistUseCase,
            observeIncomingMessagesUseCase
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(0, state.unreadChatsCount)
            assertEquals(0, state.acceptedRequestsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `linked specialist is exposed once loaded`() = runTest {
        coEvery { getUnreadChatsCountUseCase() } returns Result.success(0)
        coEvery { getUnseenAcceptedRequestsCountUseCase() } returns Result.success(0)
        coEvery { getLinkedSpecialistUseCase() } returns Result.success(linkedSpecialist)

        val viewModel = PatientHomeViewModel(
            getUnreadChatsCountUseCase,
            getUnseenAcceptedRequestsCountUseCase,
            getLinkedSpecialistUseCase,
            observeIncomingMessagesUseCase
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLinkedSpecialistLoaded)
            assertEquals("Marco", state.linkedSpecialist?.firstName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no linked specialist is reported as loaded with null`() = runTest {
        coEvery { getUnreadChatsCountUseCase() } returns Result.success(0)
        coEvery { getUnseenAcceptedRequestsCountUseCase() } returns Result.success(0)
        coEvery { getLinkedSpecialistUseCase() } returns Result.success(null)

        val viewModel = PatientHomeViewModel(
            getUnreadChatsCountUseCase,
            getUnseenAcceptedRequestsCountUseCase,
            getLinkedSpecialistUseCase,
            observeIncomingMessagesUseCase
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLinkedSpecialistLoaded)
            assertNull(state.linkedSpecialist)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
