package com.example.nutrease.ui.screens.patientstats

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nutrease.domain.model.NutrientFilter
import com.example.nutrease.domain.model.PatientStats
import com.example.nutrease.domain.usecase.GetPatientStatsUseCase
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientStatsViewModelTest {

    private val useCase: GetPatientStatsUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun savedState(): SavedStateHandle = SavedStateHandle(
        mapOf(
            PatientStatsViewModel.ARG_FASCICOLO_ID to 42,
            PatientStatsViewModel.ARG_PATIENT_NAME to "Mario Rossi"
        )
    )

    private fun fakeStats() = PatientStats(
        bmi = null,
        dailySeriesByFilter = emptyMap(),
        topSymptoms = emptyList(),
        symptomaticDaysPercent = 0.0,
        totalDays = 30
    )

    @Test
    fun `initial load populates stats and patient name`() = runTest {
        coEvery { useCase(fascicoloId = 42, today = any()) } returns Result.success(fakeStats())

        val viewModel = PatientStatsViewModel(useCase, savedState())
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Mario Rossi", state.patientName)
            assertNotNull(state.stats)
            assertEquals(NutrientFilter.LACTOSE, state.selectedNutrient)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateNutrient changes state without re-fetching`() = runTest {
        coEvery { useCase(fascicoloId = 42, today = any()) } returns Result.success(fakeStats())

        val viewModel = PatientStatsViewModel(useCase, savedState())
        advanceUntilIdle()

        viewModel.updateNutrient(NutrientFilter.SORBITOL)
        advanceUntilIdle()
        viewModel.updateNutrient(NutrientFilter.GLUTEN)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(NutrientFilter.GLUTEN, awaitItem().selectedNutrient)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { useCase(fascicoloId = 42, today = any()) }
    }

    @Test
    fun `updateNutrient ignores ALL`() = runTest {
        coEvery { useCase(fascicoloId = 42, today = any()) } returns Result.success(fakeStats())

        val viewModel = PatientStatsViewModel(useCase, savedState())
        advanceUntilIdle()

        viewModel.updateNutrient(NutrientFilter.ALL)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(NutrientFilter.LACTOSE, awaitItem().selectedNutrient)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure clears stats and exposes error`() = runTest {
        coEvery { useCase(fascicoloId = 42, today = any()) } returns
            Result.failure(IllegalStateException("boom"))

        val viewModel = PatientStatsViewModel(useCase, savedState())
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("boom", state.error)
            assertNull(state.stats)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
