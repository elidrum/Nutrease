package com.example.nutrease.ui.screens.diary

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nutrease.domain.model.Food
import com.example.nutrease.domain.model.MealType
import com.example.nutrease.domain.usecase.AddMealUseCase
import com.example.nutrease.domain.usecase.GetMealUseCase
import com.example.nutrease.domain.usecase.GetPatientFascicoloUseCase
import com.example.nutrease.domain.usecase.SearchFoodsUseCase
import com.example.nutrease.domain.usecase.UpdateMealUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddMealViewModelTest {

    private val getPatientFascicoloUseCase: GetPatientFascicoloUseCase = mockk()
    private val getMealUseCase: GetMealUseCase = mockk()
    private val searchFoodsUseCase: SearchFoodsUseCase = mockk()
    private val addMealUseCase: AddMealUseCase = mockk()
    private val updateMealUseCase: UpdateMealUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private val food = Food(
        id = 1,
        name = "Pane",
        category = null,
        lactosePer100g = 0.0,
        sorbitolPer100g = 0.0,
        glutenPer100g = 5.0,
        caloriesPer100g = 250.0,
        unitConversions = emptyMap()
    )

    // SavedStateHandle vuoto => modalità inserimento (meal_id assente).
    private fun insertViewModel() = AddMealViewModel(
        SavedStateHandle(),
        getPatientFascicoloUseCase,
        getMealUseCase,
        searchFoodsUseCase,
        addMealUseCase,
        updateMealUseCase
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `addItem then removeItem updates the item list`() = runTest {
        val viewModel = insertViewModel()
        advanceUntilIdle()

        viewModel.addItem(food, "g", 100.0)
        assertEquals(1, viewModel.uiState.value.items.size)

        viewModel.removeItem(0)
        assertEquals(0, viewModel.uiState.value.items.size)
    }

    @Test
    fun `save in insert mode calls addMeal and flags saved`() = runTest {
        coEvery { getPatientFascicoloUseCase() } returns Result.success(1)
        coEvery { addMealUseCase(any()) } returns Result.success(10L)
        val viewModel = insertViewModel()
        advanceUntilIdle()

        viewModel.addItem(food, "g", 100.0)
        viewModel.updateType(MealType.LUNCH)
        viewModel.save()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.saved)
            assertEquals(false, state.isSaving)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { addMealUseCase(any()) }
        coVerify(exactly = 0) { updateMealUseCase(any()) }
    }

    @Test
    fun `save is blocked when no meal type is selected`() = runTest {
        val viewModel = insertViewModel()
        advanceUntilIdle()

        viewModel.addItem(food, "g", 100.0)
        viewModel.save() // nessun tipo selezionato
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Seleziona il tipo di pasto", state.error)
        assertEquals(false, state.saved)
        coVerify(exactly = 0) { addMealUseCase(any()) }
        coVerify(exactly = 0) { getPatientFascicoloUseCase() }
    }

    @Test
    fun `save is blocked for a future date with an error and no insert`() = runTest {
        val viewModel = insertViewModel()
        advanceUntilIdle()

        viewModel.addItem(food, "g", 100.0)
        viewModel.updateType(MealType.LUNCH)
        viewModel.updateDate(kotlinx.datetime.LocalDate(2999, 1, 1)) // sicuramente nel futuro
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.saved)
        assertTrue(state.error?.contains("futura") == true)
        coVerify(exactly = 0) { addMealUseCase(any()) }
        coVerify(exactly = 0) { getPatientFascicoloUseCase() }
    }

    @Test
    fun `save surfaces error when fascicolo is missing`() = runTest {
        coEvery { getPatientFascicoloUseCase() } returns Result.failure(Exception("Nessun fascicolo attivo"))
        val viewModel = insertViewModel()
        advanceUntilIdle()

        viewModel.addItem(food, "g", 100.0)
        viewModel.updateType(MealType.LUNCH)
        viewModel.save()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Nessun fascicolo attivo", state.error)
            assertEquals(false, state.isSaving)
            cancelAndIgnoreRemainingEvents()
        }
    }
}