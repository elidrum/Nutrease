package com.example.nutrease.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.usecase.LoginUseCase
import com.example.nutrease.domain.usecase.SendPasswordResetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stato immutabile della schermata di login. Pattern usato da tutte le screen del
 * progetto: una sola data class per l'intero stato, aggiornata con `copy(...)` dentro
 * `MutableStateFlow.update`; la UI la osserva e si ricompone da sé.
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateTo: UserRole? = null,
    val isSendingReset: Boolean = false,
    val passwordResetSent: Boolean = false,
    /** Email a cui è stato inviato il codice: passata alla schermata di reset. */
    val resetEmail: String? = null
)

/**
 * ViewModel del login (RF3) + avvio del reset password. La UI risale solo tramite
 * chiamate ai metodi pubblici e osserva [uiState]; la navigazione post-login è
 * segnalata con `navigateTo` nello stato (la screen la consuma e naviga).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val sendPasswordResetUseCase: SendPasswordResetUseCase
) : ViewModel() {

    // Backing property: la versione mutabile resta privata, fuori esce solo
    // lo StateFlow in sola lettura (stesso schema in tutti i ViewModel).
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            loginUseCase(email.trim(), password)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false, navigateTo = user.role) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.toUiMessage()) }
                }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingReset = true, error = null) }
            sendPasswordResetUseCase(email)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSendingReset = false, passwordResetSent = true, resetEmail = email.trim())
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSendingReset = false, error = e.toUiMessage()) }
                }
        }
    }

    /**
     * Mostra solo messaggi curati: un [DomainError] espone il proprio testo generico,
     * qualunque altra eccezione collassa su un fallback neutro. Così nessun dettaglio
     * tecnico di Supabase/rete (URL, SQL, stacktrace) finisce in interfaccia.
     */
    private fun Throwable.toUiMessage(): String =
        (this as? DomainError)?.message ?: DomainError.Unknown.message.orEmpty()

    fun consumePasswordResetSent() = _uiState.update { it.copy(passwordResetSent = false) }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}