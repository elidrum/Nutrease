package com.example.nutrease.ui.screens.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.usecase.ConfirmPasswordResetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResetPasswordUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

/**
 * ViewModel del completamento reset password: l'utente inserisce il codice OTP a 6
 * cifre ricevuto via email e la nuova password. L'email arriva dalla rotta di
 * navigazione via [SavedStateHandle] (sopravvive alla process death, a differenza
 * di un parametro passato a mano al costruttore).
 */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val confirmPasswordResetUseCase: ConfirmPasswordResetUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val email: String = savedStateHandle.get<String>(ARG_EMAIL).orEmpty()

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun confirm(code: String, newPassword: String) {
        if (_uiState.value.isLoading) return // single-flight
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            confirmPasswordResetUseCase(email, code, newPassword)
                .onSuccess { _uiState.update { it.copy(isLoading = false, done = true) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.toUiMessage()) }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // I messaggi di validazione (codice/policy) sono già curati e in italiano; gli errori di
    // dominio espongono il proprio testo generico; il resto collassa su un fallback neutro.
    private fun Throwable.toUiMessage(): String = when (this) {
        is DomainError -> message.orEmpty()
        is IllegalArgumentException -> message ?: DomainError.Unknown.message.orEmpty()
        else -> DomainError.Unknown.message.orEmpty()
    }

    companion object {
        const val ARG_EMAIL = "email"
    }
}
