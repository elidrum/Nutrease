package com.example.nutrease.ui.screens.diary

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.model.SymptomType
import com.example.nutrease.domain.usecase.AddSymptomUseCase
import com.example.nutrease.domain.usecase.GetPatientFascicoloUseCase
import com.example.nutrease.domain.usecase.GetSymptomUseCase
import com.example.nutrease.domain.usecase.UpdateSymptomUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
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
class AddSymptomViewModelTest {

    private val getPatientFascicoloUseCase: GetPatientFascicoloUseCase = mockk()
    private val getSymptomUseCase: GetSymptomUseCase = mockk()
    private val addSymptomUseCase: AddSymptomUseCase = mockk()
    private val updateSymptomUseCase: UpdateSymptomUseCase = mockk()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save in insert mode marks state as saved on success`() = runTest {
        coEvery { getPatientFascicoloUseCase() } returns Result.success(42)
        coEvery { addSymptomUseCase(any()) } returns Result.success(101L)

        val viewModel = AddSymptomViewModel(
            savedStateHandle = SavedStateHandle(),
            getPatientFascicoloUseCase = getPatientFascicoloUseCase,
            getSymptomUseCase = getSymptomUseCase,
            addSymptomUseCase = addSymptomUseCase,
            updateSymptomUseCase = updateSymptomUseCase
        )

        viewModel.updateType(SymptomType.CRAMPS)
        viewModel.updateSeverity(SymptomSeverity.MODERATE)
        viewModel.save()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.saved)
            assertEquals(false, state.isSaving)
            assertEquals(SymptomType.CRAMPS, state.type)
            assertEquals(SymptomSeverity.MODERATE, state.severity)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { addSymptomUseCase(any()) }
    }

    @Test
    fun `save is blocked for a future date with an error and no insert`() = runTest {
        coEvery { getPatientFascicoloUseCase() } returns Result.success(42)

        val viewModel = AddSymptomViewModel(
            savedStateHandle = SavedStateHandle(),
            getPatientFascicoloUseCase = getPatientFascicoloUseCase,
            getSymptomUseCase = getSymptomUseCase,
            addSymptomUseCase = addSymptomUseCase,
            updateSymptomUseCase = updateSymptomUseCase
        )

        viewModel.updateDate(LocalDate(2999, 1, 1)) // sicuramente nel futuro
        viewModel.save()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(false, state.saved)
            assertTrue(state.error?.contains("futura") == true)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { addSymptomUseCase(any()) }
    }

    @Test
    fun `save is blocked when OTHER type has no note`() = runTest {
        coEvery { getPatientFascicoloUseCase() } returns Result.success(42)

        val viewModel = AddSymptomViewModel(
            savedStateHandle = SavedStateHandle(),
            getPatientFascicoloUseCase = getPatientFascicoloUseCase,
            getSymptomUseCase = getSymptomUseCase,
            addSymptomUseCase = addSymptomUseCase,
            updateSymptomUseCase = updateSymptomUseCase
        )

        viewModel.updateType(SymptomType.OTHER) // nessuna nota inserita
        viewModel.save()
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().saved)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { addSymptomUseCase(any()) }
    }

    @Test
    fun `save with OTHER type forwards the free-text note`() = runTest {
        val captured = slot<Symptom>()
        coEvery { getPatientFascicoloUseCase() } returns Result.success(42)
        coEvery { addSymptomUseCase(capture(captured)) } returns Result.success(7L)

        val viewModel = AddSymptomViewModel(
            savedStateHandle = SavedStateHandle(),
            getPatientFascicoloUseCase = getPatientFascicoloUseCase,
            getSymptomUseCase = getSymptomUseCase,
            addSymptomUseCase = addSymptomUseCase,
            updateSymptomUseCase = updateSymptomUseCase
        )

        viewModel.updateType(SymptomType.OTHER)
        viewModel.updateNote("Mal di testa")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(SymptomType.OTHER, captured.captured.type)
        assertEquals("Mal di testa", captured.captured.note)
    }

    @Test
    fun `save in edit mode propagates failure as error`() = runTest {
        val savedState = SavedStateHandle().apply { set(ARG_SYMPTOM_ID, 7L) }
        val existing = Symptom(
            id = 7L,
            fascicoloId = 42,
            date = kotlinx.datetime.LocalDate(2026, 5, 1),
            time = kotlinx.datetime.LocalTime(10, 30),
            type = SymptomType.NAUSEA,
            severity = SymptomSeverity.MILD
        )
        coEvery { getSymptomUseCase(7L) } returns Result.success(existing)
        coEvery { updateSymptomUseCase(any()) } returns Result.failure(IllegalStateException("boom"))

        val viewModel = AddSymptomViewModel(
            savedStateHandle = savedState,
            getPatientFascicoloUseCase = getPatientFascicoloUseCase,
            getSymptomUseCase = getSymptomUseCase,
            addSymptomUseCase = addSymptomUseCase,
            updateSymptomUseCase = updateSymptomUseCase
        )

        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(false, state.saved)
            assertEquals("boom", state.error)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { updateSymptomUseCase(any()) }
    }
}