package com.example.nutrease.ui.screens.patientdiary

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nutrease.domain.model.DiaryDateRange
import com.example.nutrease.domain.model.MealFoodItem
import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.model.MealType
import com.example.nutrease.domain.model.NutrientFilter
import com.example.nutrease.domain.model.NutrientTotals
import com.example.nutrease.domain.model.PatientDiaryDay
import com.example.nutrease.domain.usecase.GetPatientDiaryRangeUseCase
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientDiaryViewModelTest {

    private val useCase: GetPatientDiaryRangeUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun savedState(): SavedStateHandle = SavedStateHandle(
        mapOf(
            PatientDiaryViewModel.ARG_FASCICOLO_ID to 42,
            PatientDiaryViewModel.ARG_PATIENT_NAME to "Mario Rossi"
        )
    )

    private fun day(date: LocalDate, lactose: Double): PatientDiaryDay = PatientDiaryDay(
        date = date,
        meals = listOf(
            Meal(
                id = 1L,
                fascicoloId = 42,
                date = date,
                time = LocalTime(12, 0),
                type = MealType.LUNCH,
                description = null,
                items = listOf(
                    MealFoodItem(
                        id = 1L,
                        foodId = 1,
                        foodName = "Test",
                        quantityGrams = 100.0,
                        originalUnit = "g",
                        originalQuantity = 100.0,
                        nutrients = NutrientTotals(
                            lactose = lactose,
                            calories = 200.0
                        )
                    )
                )
            )
        ),
        symptoms = emptyList(),
        totals = NutrientTotals(lactose = lactose, calories = 200.0)
    )

    @Test
    fun `initial load populates state with patient name and period totals`() = runTest {
        coEvery {
            useCase(fascicoloId = 42, range = DiaryDateRange.Last7Days, today = any())
        } returns Result.success(
            listOf(
                day(LocalDate(2026, 5, 27), 3.0),
                day(LocalDate(2026, 5, 26), 2.0)
            )
        )

        val viewModel = PatientDiaryViewModel(useCase, savedState())
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Mario Rossi", state.patientName)
            assertEquals(2, state.days.size)
            assertEquals(5.0, state.periodTotals.lactose, 0.0001)
            assertEquals(400.0, state.periodTotals.calories, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateRange re-fetches with the new range`() = runTest {
        coEvery {
            useCase(fascicoloId = 42, range = DiaryDateRange.Last7Days, today = any())
        } returns Result.success(emptyList())
        coEvery {
            useCase(fascicoloId = 42, range = DiaryDateRange.Today, today = any())
        } returns Result.success(listOf(day(LocalDate(2026, 5, 27), 1.0)))

        val viewModel = PatientDiaryViewModel(useCase, savedState())
        advanceUntilIdle()

        viewModel.updateRange(DiaryDateRange.Today)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(DiaryDateRange.Today, state.range)
            assertEquals(1, state.days.size)
            assertEquals(1.0, state.periodTotals.lactose, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) {
            useCase(fascicoloId = 42, range = DiaryDateRange.Today, today = any())
        }
    }

    @Test
    fun `updateNutrientFilter does not re-fetch but updates state`() = runTest {
        coEvery {
            useCase(fascicoloId = 42, range = DiaryDateRange.Last7Days, today = any())
        } returns Result.success(emptyList())

        val viewModel = PatientDiaryViewModel(useCase, savedState())
        advanceUntilIdle()

        viewModel.updateNutrientFilter(NutrientFilter.LACTOSE)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(NutrientFilter.LACTOSE, state.nutrientFilter)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) {
            useCase(fascicoloId = 42, range = DiaryDateRange.Last7Days, today = any())
        }
    }

    @Test
    fun `failure clears days and exposes the error`() = runTest {
        coEvery {
            useCase(fascicoloId = 42, range = DiaryDateRange.Last7Days, today = any())
        } returns Result.failure(IllegalStateException("boom"))

        val viewModel = PatientDiaryViewModel(useCase, savedState())
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("boom", state.error)
            assertEquals(emptyList<PatientDiaryDay>(), state.days)
            cancelAndIgnoreRemainingEvents()
        }
    }
}