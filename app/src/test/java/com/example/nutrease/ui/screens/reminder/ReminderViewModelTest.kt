package com.example.nutrease.ui.screens.reminder

import app.cash.turbine.test
import com.example.nutrease.domain.model.ReminderConfig
import com.example.nutrease.domain.usecase.DeleteReminderUseCase
import com.example.nutrease.domain.usecase.GetReminderConfigsUseCase
import com.example.nutrease.domain.usecase.SaveReminderConfigUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {

    private val getConfigs: GetReminderConfigsUseCase = mockk()
    private val saveConfig: SaveReminderConfigUseCase = mockk()
    private val deleteReminder: DeleteReminderUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun reminder(id: Long, time: LocalTime = LocalTime(20, 0), enabled: Boolean = true) =
        ReminderConfig(
            id = id,
            patientTaxCode = "X",
            enabled = enabled,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
            time = time,
            message = null
        )

    private fun vm() = ReminderViewModel(getConfigs, saveConfig, deleteReminder)

    @Test
    fun `init loads existing reminders into state`() = runTest {
        coEvery { getConfigs() } returns Result.success(listOf(reminder(1), reminder(2)))

        val viewModel = vm()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(2, state.reminders.size)
            assertTrue(state.hasEnabledReminder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openNewReminder opens an empty editor`() = runTest {
        coEvery { getConfigs() } returns Result.success(emptyList())

        val viewModel = vm()
        advanceUntilIdle()
        viewModel.openNewReminder()

        viewModel.uiState.test {
            val editor = awaitItem().editor
            assertTrue(editor != null)
            assertNull(editor!!.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editorToggleDay flips selection in the editor`() = runTest {
        coEvery { getConfigs() } returns Result.success(emptyList())

        val viewModel = vm()
        advanceUntilIdle()
        viewModel.openNewReminder()
        viewModel.editorToggleDay(DayOfWeek.MONDAY) // era selezionato di default -> rimosso

        viewModel.uiState.test {
            assertFalse(DayOfWeek.MONDAY in awaitItem().editor!!.daysOfWeek)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveEditor persists, closes editor and reloads`() = runTest {
        coEvery { getConfigs() } returnsMany listOf(
            Result.success(emptyList()),
            Result.success(listOf(reminder(10)))
        )
        val captured = slot<ReminderConfig>()
        coEvery { saveConfig(capture(captured)) } answers {
            Result.success(captured.captured.copy(id = 10))
        }

        val viewModel = vm()
        advanceUntilIdle()
        viewModel.openNewReminder()
        viewModel.saveEditor()
        advanceUntilIdle()

        coVerify(exactly = 1) { saveConfig(any()) }
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.editor)
            assertTrue(state.savedSnackbar)
            assertEquals(1, state.reminders.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveEditor with no days surfaces error and does not save`() = runTest {
        coEvery { getConfigs() } returns Result.success(emptyList())

        val viewModel = vm()
        advanceUntilIdle()
        viewModel.openNewReminder()
        // svuota tutti i giorni
        DayOfWeek.entries.forEach { viewModel.editorToggleDay(it) }
        viewModel.saveEditor()
        advanceUntilIdle()

        coVerify(exactly = 0) { saveConfig(any()) }
        viewModel.uiState.test {
            assertTrue(awaitItem().error != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleReminderEnabled saves the reminder with flipped flag`() = runTest {
        coEvery { getConfigs() } returns Result.success(listOf(reminder(3, enabled = true)))
        val captured = slot<ReminderConfig>()
        coEvery { saveConfig(capture(captured)) } answers {
            Result.success(captured.captured)
        }

        val viewModel = vm()
        advanceUntilIdle()
        viewModel.toggleReminderEnabled(reminder(3, enabled = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { saveConfig(any()) }
        assertFalse(captured.captured.enabled)
    }

    @Test
    fun `deleteReminder delegates to use case`() = runTest {
        coEvery { getConfigs() } returns Result.success(listOf(reminder(4)))
        coEvery { deleteReminder.invoke(4) } returns Result.success(Unit)

        val viewModel = vm()
        advanceUntilIdle()
        viewModel.deleteReminder(4)
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteReminder.invoke(4) }
    }

    @Test
    fun `addMealPresets saves three reminders`() = runTest {
        coEvery { getConfigs() } returns Result.success(emptyList())
        coEvery { saveConfig(any()) } answers { Result.success(firstArg<ReminderConfig>().copy(id = 1)) }

        val viewModel = vm()
        advanceUntilIdle()
        viewModel.addMealPresets()
        advanceUntilIdle()

        coVerify(exactly = 3) { saveConfig(any()) }
    }
}
