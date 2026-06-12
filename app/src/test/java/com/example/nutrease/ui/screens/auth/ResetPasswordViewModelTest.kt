package com.example.nutrease.ui.screens.auth

import androidx.lifecycle.SavedStateHandle
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.usecase.ConfirmPasswordResetUseCase
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {

    private val confirmUseCase: ConfirmPasswordResetUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private fun viewModel(email: String = "u@x.it") = ResetPasswordViewModel(
        confirmUseCase,
        SavedStateHandle(mapOf(ResetPasswordViewModel.ARG_EMAIL to email))
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `successful confirm flags done`() = runTest {
        coEvery { confirmUseCase("u@x.it", "123456", "NewPass1") } returns Result.success(Unit)
        val vm = viewModel()

        vm.confirm("123456", "NewPass1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.done)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `failed confirm surfaces curated error and does not finish`() = runTest {
        coEvery { confirmUseCase(any(), any(), any()) } returns Result.failure(DomainError.InvalidResetCode)
        val vm = viewModel()

        vm.confirm("000000", "NewPass1")
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.done)
        assertEquals(DomainError.InvalidResetCode.message, vm.uiState.value.error)
    }
}
