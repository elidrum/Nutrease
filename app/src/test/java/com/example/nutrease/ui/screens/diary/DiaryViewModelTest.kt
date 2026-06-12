package com.example.nutrease.ui.screens.diary

import app.cash.turbine.test
import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.model.MealType
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.model.SymptomType
import com.example.nutrease.domain.usecase.DeleteMealUseCase
import com.example.nutrease.domain.usecase.DeleteSymptomUseCase
import com.example.nutrease.domain.usecase.GetMealsForDateUseCase
import com.example.nutrease.domain.usecase.GetPatientFascicoloUseCase
import com.example.nutrease.domain.usecase.GetSymptomsForDateUseCase
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
class DiaryViewModelTest {

    private val getPatientFascicoloUseCase: GetPatientFascicoloUseCase = mockk()
    private val getMealsForDateUseCase: GetMealsForDateUseCase = mockk()
    private val getSymptomsForDateUseCase: GetSymptomsForDateUseCase = mockk()
    private val deleteMealUseCase: DeleteMealUseCase = mockk()
    private val deleteSymptomUseCase: DeleteSymptomUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private val meal = Meal(
        id = 5L,
        fascicoloId = 1,
        date = LocalDate(2026, 5, 31),
        time = LocalTime(12, 0),
        type = MealType.LUNCH,
        description = null,
        items = emptyList()
    )
    private val symptom = Symptom(
        id = 7L,
        fascicoloId = 1,
        date = LocalDate(2026, 5, 31),
        time = LocalTime(15, 0),
        type = SymptomType.BLOATING,
        severity = SymptomSeverity.MILD
    )

    private fun buildViewModel(): DiaryViewModel {
        coEvery { getPatientFascicoloUseCase() } returns Result.success(1)
        coEvery { getMealsForDateUseCase(1, any()) } returns Result.success(listOf(meal))
        coEvery { getSymptomsForDateUseCase(1, any()) } returns Result.success(listOf(symptom))
        return DiaryViewModel(
            getPatientFascicoloUseCase,
            getMealsForDateUseCase,
            getSymptomsForDateUseCase,
            deleteMealUseCase,
            deleteSymptomUseCase
        )
    }

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init loads meals and symptoms for the day`() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.meals.size)
            assertEquals(1, state.symptoms.size)
            assertEquals(1, state.fascicoloId)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteMeal success sets message and refreshes`() = runTest {
        coEvery { deleteMealUseCase(5L) } returns Result.success(Unit)
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.deleteMeal(5L)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals("Pasto eliminato", awaitItem().successMessage)
            cancelAndIgnoreRemainingEvents()
        }
        // initial load + refresh after delete = 2 fetch dei pasti
        coVerify(exactly = 2) { getMealsForDateUseCase(1, any()) }
    }

    @Test
    fun `fascicolo failure surfaces error`() = runTest {
        coEvery { getPatientFascicoloUseCase() } returns Result.failure(Exception("Nessun fascicolo attivo"))
        val viewModel = DiaryViewModel(
            getPatientFascicoloUseCase,
            getMealsForDateUseCase,
            getSymptomsForDateUseCase,
            deleteMealUseCase,
            deleteSymptomUseCase
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Nessun fascicolo attivo", state.error)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}