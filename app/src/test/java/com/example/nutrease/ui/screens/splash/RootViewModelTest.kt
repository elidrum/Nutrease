package com.example.nutrease.ui.screens.splash

import app.cash.turbine.test
import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.usecase.RescheduleRemindersUseCase
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private val authRepository: AuthRepository = mockk()
    private val rescheduleRemindersUseCase: RescheduleRemindersUseCase = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun user(role: UserRole) = AuthUser("uid", "u@x.it", role, "CF")

    @Test
    fun `patient session routes to patient home`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user(UserRole.PATIENT)

        val viewModel = RootViewModel(authRepository, rescheduleRemindersUseCase)
        advanceUntilIdle()

        viewModel.state.test {
            assertEquals(RootViewModel.StartState.PatientHome, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `specialist session routes to specialist home`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user(UserRole.SPECIALIST)

        val viewModel = RootViewModel(authRepository, rescheduleRemindersUseCase)
        advanceUntilIdle()

        viewModel.state.test {
            assertEquals(RootViewModel.StartState.SpecialistHome, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no session routes to login`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        val viewModel = RootViewModel(authRepository, rescheduleRemindersUseCase)
        advanceUntilIdle()

        viewModel.state.test {
            assertEquals(RootViewModel.StartState.Login, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}