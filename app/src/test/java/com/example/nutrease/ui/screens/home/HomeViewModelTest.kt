package com.example.nutrease.ui.screens.home

import app.cash.turbine.test
import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.Gender
import com.example.nutrease.domain.model.Patient
import com.example.nutrease.domain.model.UserProfile
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.usecase.GetProfileUseCase
import com.example.nutrease.domain.usecase.LogoutUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val logoutUseCase: LogoutUseCase = mockk()
    private val getProfileUseCase: GetProfileUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private val patientProfile = UserProfile.PatientProfile(
        authUser = AuthUser("uid", "mario@example.com", UserRole.PATIENT, "CF"),
        patient = Patient(
            taxCode = "CF",
            authUid = "uid",
            firstName = "Mario",
            lastName = "Rossi",
            email = "mario@example.com",
            phone = null,
            gender = Gender.M,
            birthDate = LocalDate(1980, 1, 1),
            street = null,
            city = null,
            postalCode = null
        )
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init loads user first name`() = runTest {
        coEvery { getProfileUseCase() } returns Result.success(patientProfile)
        val viewModel = HomeViewModel(logoutUseCase, getProfileUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals("Mario", awaitItem().userName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `logout requests navigation to login`() = runTest {
        coEvery { getProfileUseCase() } returns Result.failure(Exception("no profile"))
        coEvery { logoutUseCase() } returns Unit
        val viewModel = HomeViewModel(logoutUseCase, getProfileUseCase)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        viewModel.uiState.test {
            assertTrue(awaitItem().navigateToLogin)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { logoutUseCase() }
    }
}