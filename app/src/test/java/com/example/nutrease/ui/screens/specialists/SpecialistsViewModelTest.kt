package com.example.nutrease.ui.screens.specialists

import app.cash.turbine.test
import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.SpecializationType
import com.example.nutrease.domain.usecase.GetLinkedSpecialistUseCase
import com.example.nutrease.domain.usecase.MarkAcceptedRequestsSeenUseCase
import com.example.nutrease.domain.usecase.SearchSpecialistsUseCase
import com.example.nutrease.domain.usecase.SendLinkRequestUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpecialistsViewModelTest {

    private val searchSpecialistsUseCase: SearchSpecialistsUseCase = mockk()
    private val sendLinkRequestUseCase: SendLinkRequestUseCase = mockk()
    private val markAcceptedRequestsSeenUseCase: MarkAcceptedRequestsSeenUseCase = mockk(relaxed = true)
    private val getLinkedSpecialistUseCase: GetLinkedSpecialistUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private fun specialist(taxCode: String, lastName: String) = Specialist(
        taxCode = taxCode,
        authUid = "uid-$taxCode",
        firstName = "Mario",
        lastName = lastName,
        email = "$taxCode@example.com",
        phone = null,
        vatNumber = "11111111111",
        info = null,
        specialization = null,
        city = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default: paziente non collegato; i test sul banner sovrascrivono lo stub.
        coEvery { getLinkedSpecialistUseCase() } returns Result.success(null)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial load fills the state with first page results`() = runTest {
        coEvery {
            searchSpecialistsUseCase(query = "", page = 0, specialization = null, city = "")
        } returns Result.success(
            listOf(specialist("CF1", "Rossi"), specialist("CF2", "Bianchi"))
        )

        val viewModel = SpecialistsViewModel(
            searchSpecialistsUseCase,
            sendLinkRequestUseCase,
            markAcceptedRequestsSeenUseCase,
            getLinkedSpecialistUseCase
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.specialists.size)
            assertEquals("CF1", state.specialists.first().taxCode)
            assertEquals(false, state.isLoadingPage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query updates trigger a single debounced search`() = runTest {
        coEvery {
            searchSpecialistsUseCase(query = "", page = 0, specialization = null, city = "")
        } returns Result.success(emptyList())
        coEvery {
            searchSpecialistsUseCase(query = "Maria", page = 0, specialization = null, city = "")
        } returns Result.success(listOf(specialist("CF9", "Maria")))

        val viewModel = SpecialistsViewModel(
            searchSpecialistsUseCase,
            sendLinkRequestUseCase,
            markAcceptedRequestsSeenUseCase,
            getLinkedSpecialistUseCase
        )
        advanceUntilIdle()

        viewModel.updateQuery("M")
        advanceTimeBy(100)
        viewModel.updateQuery("Ma")
        advanceTimeBy(100)
        viewModel.updateQuery("Maria")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.specialists.size)
            assertEquals("Maria", state.specialists.first().lastName)
            assertTrue(state.query == "Maria")
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) {
            searchSpecialistsUseCase(query = "Maria", page = 0, specialization = null, city = "")
        }
    }

    @Test
    fun `specialization filter is forwarded to the use case`() = runTest {
        coEvery {
            searchSpecialistsUseCase(query = "", page = 0, specialization = null, city = "")
        } returns Result.success(emptyList())
        coEvery {
            searchSpecialistsUseCase(
                query = "",
                page = 0,
                specialization = SpecializationType.NUTRIZIONISTA,
                city = ""
            )
        } returns Result.success(listOf(specialist("CFX", "Verdi")))

        val viewModel = SpecialistsViewModel(
            searchSpecialistsUseCase,
            sendLinkRequestUseCase,
            markAcceptedRequestsSeenUseCase,
            getLinkedSpecialistUseCase
        )
        advanceUntilIdle()

        viewModel.updateSpecialization(SpecializationType.NUTRIZIONISTA)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.specialists.size)
            assertEquals(SpecializationType.NUTRIZIONISTA, state.specializationFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmLinkRequest removes the target specialist from the list on success`() = runTest {
        val target = specialist("CFT", "Rossi")
        coEvery {
            searchSpecialistsUseCase(query = "", page = 0, specialization = null, city = "")
        } returns Result.success(listOf(target, specialist("CFO", "Bianchi")))
        coEvery {
            sendLinkRequestUseCase(specialistTaxCode = "CFT", message = null)
        } returns Result.success(
            LinkRequest(
                id = 1L,
                patientTaxCode = "P1",
                specialistTaxCode = "CFT",
                status = LinkRequestStatus.PENDING,
                message = null,
                requestedAt = Instant.fromEpochMilliseconds(0),
                respondedAt = null,
                rejectionReason = null
            )
        )

        val viewModel = SpecialistsViewModel(
            searchSpecialistsUseCase,
            sendLinkRequestUseCase,
            markAcceptedRequestsSeenUseCase,
            getLinkedSpecialistUseCase
        )
        advanceUntilIdle()

        viewModel.startLinkRequest(target)
        viewModel.confirmLinkRequest(message = null)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.specialists.size)
            assertEquals("CFO", state.specialists.first().taxCode)
            assertNull(state.pendingLinkRequestFor)
            assertTrue(state.linkRequestSent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `linked specialist name feeds the replacement warning banner`() = runTest {
        coEvery {
            searchSpecialistsUseCase(query = "", page = 0, specialization = null, city = "")
        } returns Result.success(emptyList())
        coEvery { getLinkedSpecialistUseCase() } returns Result.success(
            specialist("CFL", "Bianchi")
        )

        val viewModel = SpecialistsViewModel(
            searchSpecialistsUseCase,
            sendLinkRequestUseCase,
            markAcceptedRequestsSeenUseCase,
            getLinkedSpecialistUseCase
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Mario Bianchi", state.linkedSpecialistName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startLinkRequest retries the linked specialist load if the first fetch failed`() = runTest {
        coEvery {
            searchSpecialistsUseCase(query = "", page = 0, specialization = null, city = "")
        } returns Result.success(emptyList())
        // Init fallisce (es. rete), il retry al momento del dialog riesce.
        coEvery { getLinkedSpecialistUseCase() } returnsMany listOf(
            Result.failure(IllegalStateException("network")),
            Result.success(specialist("CFL", "Bianchi"))
        )

        val viewModel = SpecialistsViewModel(
            searchSpecialistsUseCase,
            sendLinkRequestUseCase,
            markAcceptedRequestsSeenUseCase,
            getLinkedSpecialistUseCase
        )
        advanceUntilIdle()

        viewModel.startLinkRequest(specialist("CFT", "Rossi"))
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals("Mario Bianchi", awaitItem().linkedSpecialistName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no linked specialist leaves the warning banner hidden`() = runTest {
        coEvery {
            searchSpecialistsUseCase(query = "", page = 0, specialization = null, city = "")
        } returns Result.success(emptyList())

        val viewModel = SpecialistsViewModel(
            searchSpecialistsUseCase,
            sendLinkRequestUseCase,
            markAcceptedRequestsSeenUseCase,
            getLinkedSpecialistUseCase
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            assertNull(awaitItem().linkedSpecialistName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}