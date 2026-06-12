package com.example.nutrease.ui.screens.profile

import app.cash.turbine.test
import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.model.Gender
import com.example.nutrease.domain.model.Patient
import com.example.nutrease.domain.model.UserProfile
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.usecase.ChangePasswordUseCase
import com.example.nutrease.domain.usecase.DeleteAccountUseCase
import com.example.nutrease.domain.usecase.GetProfileUseCase
import com.example.nutrease.domain.usecase.LogoutUseCase
import com.example.nutrease.domain.usecase.UpdatePatientUseCase
import com.example.nutrease.domain.usecase.UpdateSpecialistUseCase
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
class ProfileViewModelTest {

    private val getProfileUseCase: GetProfileUseCase = mockk()
    private val updatePatientUseCase: UpdatePatientUseCase = mockk()
    private val updateSpecialistUseCase: UpdateSpecialistUseCase = mockk()
    private val changePasswordUseCase: ChangePasswordUseCase = mockk()
    private val logoutUseCase: LogoutUseCase = mockk()
    private val deleteAccountUseCase: DeleteAccountUseCase = mockk()
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

    private fun buildViewModel() = ProfileViewModel(
        getProfileUseCase, updatePatientUseCase, updateSpecialistUseCase,
        changePasswordUseCase, logoutUseCase, deleteAccountUseCase
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init loads profile`() = runTest {
        coEvery { getProfileUseCase() } returns Result.success(patientProfile)
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(patientProfile, state.profile)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changePassword success sets success message`() = runTest {
        coEvery { getProfileUseCase() } returns Result.success(patientProfile)
        coEvery { changePasswordUseCase("old", "Password1") } returns Result.success(Unit)
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.changePassword("old", "Password1")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Password aggiornata", state.successMessage)
            assertEquals(false, state.isSaving)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { changePasswordUseCase("old", "Password1") }
    }

    @Test
    fun `deleteAccount success navigates to login`() = runTest {
        coEvery { getProfileUseCase() } returns Result.success(patientProfile)
        coEvery { deleteAccountUseCase("Password1") } returns Result.success(Unit)
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.deleteAccount("Password1")
        advanceUntilIdle()

        viewModel.uiState.test {
            assertTrue(awaitItem().navigateToLogin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAccount failure surfaces domain error message and stays`() = runTest {
        coEvery { getProfileUseCase() } returns Result.success(patientProfile)
        coEvery { deleteAccountUseCase("Password1") } returns
            Result.failure(DomainError.StillHasLinkedPatients)
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.deleteAccount("Password1")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(DomainError.StillHasLinkedPatients.message, state.error)
            assertEquals(false, state.navigateToLogin)
            assertEquals(false, state.isSaving)
            cancelAndIgnoreRemainingEvents()
        }
    }
}