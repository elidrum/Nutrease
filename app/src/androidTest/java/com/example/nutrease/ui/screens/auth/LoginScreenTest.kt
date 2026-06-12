package com.example.nutrease.ui.screens.auth

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nutrease.R
import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.RegisterData
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.usecase.LoginUseCase
import com.example.nutrease.domain.usecase.SendPasswordResetUseCase
import com.example.nutrease.ui.theme.NutreaseTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Test UI del flusso critico di login. Usa un fake di
 * [AuthRepository] iniettato in un ViewModel reale: niente Hilt, niente emulator
 * di backend. Verifica che, su credenziali valide, la schermata richieda la
 * navigazione alla home del paziente.
 */
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class FakeAuthRepository(
        private val onLogin: (String, String) -> Result<AuthUser>
    ) : AuthRepository {
        override suspend fun login(email: String, password: String): Result<AuthUser> = onLogin(email, password)
        override suspend fun register(data: RegisterData): Result<Unit> = Result.success(Unit)
        override suspend fun getCurrentUser(): AuthUser? = null
        override suspend fun logout() {}
        override suspend fun reauthenticate(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    }

    private fun viewModelWith(onLogin: (String, String) -> Result<AuthUser>): AuthViewModel {
        val repo = FakeAuthRepository(onLogin)
        return AuthViewModel(LoginUseCase(repo), SendPasswordResetUseCase(repo))
    }

    @Test
    fun successfulLogin_requestsNavigationToPatientHome() {
        var navigatedToPatient = false
        val viewModel = viewModelWith { _, _ ->
            Result.success(AuthUser("uid", "mario@example.com", UserRole.PATIENT, "CF"))
        }

        composeTestRule.setContent {
            NutreaseTheme {
                LoginScreen(
                    onNavigateToPatientHome = { navigatedToPatient = true },
                    onNavigateToSpecialistHome = {},
                    onNavigateToRegister = {},
                    onNavigateToResetPassword = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.common_email))
            .performTextInput("mario@example.com")
        composeTestRule.onNodeWithText(context.getString(R.string.common_password))
            .performTextInput("Password1")
        composeTestRule.onNodeWithText(context.getString(R.string.login_submit))
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedToPatient }
        assertTrue(navigatedToPatient)
    }
}