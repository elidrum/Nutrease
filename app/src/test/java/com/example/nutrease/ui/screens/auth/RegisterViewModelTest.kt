package com.example.nutrease.ui.screens.auth

import app.cash.turbine.test
import com.example.nutrease.domain.model.Gender
import com.example.nutrease.domain.model.RegisterData
import com.example.nutrease.domain.model.SpecializationType
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.usecase.RegisterUseCase
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val registerUseCase: RegisterUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    // Codice fiscale fittizio di 16 caratteri + password conforme alla policy.
    private val taxCode = "RSSMRA80A01H501U"
    private val validPassword = "Password1"

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `registerPatient with blank name fails validation without calling use case`() = runTest {
        val viewModel = RegisterViewModel(registerUseCase)

        viewModel.registerPatient(
            email = "mario@example.com",
            password = validPassword,
            firstName = "   ",
            lastName = "Rossi",
            taxCode = taxCode,
            gender = Gender.M,
            birthDate = LocalDate(1980, 1, 1)
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.error)
            assertNull(state.navigateTo)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { registerUseCase(any()) }
    }

    @Test
    fun `registerPatient valid navigates to patient home`() = runTest {
        coEvery { registerUseCase(any<RegisterData.PatientData>()) } returns Result.success(Unit)
        val viewModel = RegisterViewModel(registerUseCase)

        viewModel.registerPatient(
            email = "mario@example.com",
            password = validPassword,
            firstName = "Mario",
            lastName = "Rossi",
            taxCode = taxCode,
            gender = Gender.M,
            birthDate = LocalDate(1980, 1, 1)
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(UserRole.PATIENT, state.navigateTo)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { registerUseCase(any<RegisterData.PatientData>()) }
    }

    @Test
    fun `registerPatient under minimum age is blocked with error`() = runTest {
        val viewModel = RegisterViewModel(registerUseCase)

        viewModel.registerPatient(
            email = "kid@example.com",
            password = validPassword,
            firstName = "Luca",
            lastName = "Bianchi",
            taxCode = taxCode,
            gender = Gender.M,
            birthDate = LocalDate(2020, 1, 1) // chiaramente minorenne
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.error)
            assertNull(state.navigateTo)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { registerUseCase(any()) }
    }

    @Test
    fun `registerSpecialist with invalid vat shows error`() = runTest {
        val viewModel = RegisterViewModel(registerUseCase)

        viewModel.registerSpecialist(
            email = "spec@example.com",
            password = validPassword,
            firstName = "Anna",
            lastName = "Verdi",
            taxCode = taxCode,
            vatNumber = "123", // non valida (deve essere 11 cifre)
            specialization = SpecializationType.NUTRIZIONISTA,
            city = "Ancona"
        )
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.error)
            assertNull(state.navigateTo)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { registerUseCase(any()) }
    }
}