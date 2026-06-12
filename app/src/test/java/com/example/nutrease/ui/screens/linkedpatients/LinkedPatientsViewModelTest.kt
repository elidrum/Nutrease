package com.example.nutrease.ui.screens.linkedpatients

import app.cash.turbine.test
import com.example.nutrease.domain.model.LinkedPatient
import com.example.nutrease.domain.usecase.GetLinkedPatientsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkedPatientsViewModelTest {

    private val getLinkedPatientsUseCase: GetLinkedPatientsUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private fun patient(taxCode: String, lastName: String) = LinkedPatient(
        taxCode = taxCode,
        fascicoloId = taxCode.hashCode(),
        firstName = "Mario",
        lastName = lastName,
        email = "$taxCode@example.com",
        birthDate = LocalDate(1980, 1, 1)
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial load fills the state on success`() = runTest {
        coEvery { getLinkedPatientsUseCase() } returns
            Result.success(listOf(patient("CF1", "Rossi"), patient("CF2", "Bianchi")))

        val viewModel = LinkedPatientsViewModel(getLinkedPatientsUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.patients.size)
            assertTrue(!state.isLoading)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty result keeps an empty list without error`() = runTest {
        coEvery { getLinkedPatientsUseCase() } returns Result.success(emptyList())

        val viewModel = LinkedPatientsViewModel(getLinkedPatientsUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.patients.isEmpty())
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure surfaces the error message`() = runTest {
        coEvery { getLinkedPatientsUseCase() } returns
            Result.failure(IllegalStateException("boom"))

        val viewModel = LinkedPatientsViewModel(getLinkedPatientsUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("boom", state.error)
            assertTrue(state.patients.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}