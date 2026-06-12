package com.example.nutrease.ui.screens.auth

import app.cash.turbine.test
import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.usecase.LoginUseCase
import com.example.nutrease.domain.usecase.SendPasswordResetUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val loginUseCase: LoginUseCase = mockk()
    private val sendPasswordResetUseCase: SendPasswordResetUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `login success routes to user role`() = runTest {
        coEvery { loginUseCase("mario@example.com", "Password1") } returns
            Result.success(AuthUser("uid", "mario@example.com", UserRole.PATIENT, "CF"))
        val viewModel = AuthViewModel(loginUseCase, sendPasswordResetUseCase)

        viewModel.login("  mario@example.com  ", "Password1")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(UserRole.PATIENT, state.navigateTo)
            assertEquals(false, state.isLoading)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { loginUseCase("mario@example.com", "Password1") }
    }

    @Test
    fun `login failure with DomainError surfaces its generic message`() = runTest {
        coEvery { loginUseCase(any(), any()) } returns Result.failure(DomainError.InvalidCredentials)
        val viewModel = AuthViewModel(loginUseCase, sendPasswordResetUseCase)

        viewModel.login("x@y.it", "wrong")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Credenziali non valide", state.error)
            assertEquals(false, state.isLoading)
            assertNull(state.navigateTo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login failure with raw exception never leaks technical detail`() = runTest {
        // Una eccezione "tecnica" che sfugge alla mappatura non deve raggiungere la UI:
        // collassa sul messaggio neutro, mai il testo grezzo di Supabase.
        coEvery { loginUseCase(any(), any()) } returns
            Result.failure(Exception("PostgrestException URL: https://x.supabase.co/... 400"))
        val viewModel = AuthViewModel(loginUseCase, sendPasswordResetUseCase)

        viewModel.login("x@y.it", "wrong")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(DomainError.Unknown.message, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendPasswordReset success flags reset sent`() = runTest {
        coEvery { sendPasswordResetUseCase("x@y.it") } returns Result.success(Unit)
        val viewModel = AuthViewModel(loginUseCase, sendPasswordResetUseCase)

        viewModel.sendPasswordReset("x@y.it")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.passwordResetSent)
            assertEquals(false, state.isSendingReset)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.consumePasswordResetSent()
        assertEquals(false, viewModel.uiState.value.passwordResetSent)
    }
}