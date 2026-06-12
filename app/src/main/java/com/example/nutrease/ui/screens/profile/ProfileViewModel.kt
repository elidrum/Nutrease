package com.example.nutrease.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.model.Patient
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.UserProfile
import com.example.nutrease.domain.usecase.ChangePasswordUseCase
import com.example.nutrease.domain.usecase.DeleteAccountUseCase
import com.example.nutrease.domain.usecase.GetProfileUseCase
import com.example.nutrease.domain.usecase.LogoutUseCase
import com.example.nutrease.domain.usecase.UpdatePatientUseCase
import com.example.nutrease.domain.usecase.UpdateSpecialistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val showPasswordDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val navigateToLogin: Boolean = false,
    val successMessage: String? = null
)

/**
 * ViewModel del profilo (RF5–RF7): caricamento/salvataggio anagrafica per ruolo,
 * cambio password e eliminazione account (entrambe le operazioni sensibili chiedono
 * la password nelle dialog e ripassano dalla re-auth negli UseCase), logout.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val updatePatientUseCase: UpdatePatientUseCase,
    private val updateSpecialistUseCase: UpdateSpecialistUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getProfileUseCase()
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoading = false, profile = profile) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun savePatient(patient: Patient) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            updatePatientUseCase(patient)
                .onSuccess {
                    val updated = (_uiState.value.profile as? UserProfile.PatientProfile)
                        ?.copy(patient = patient)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            profile = updated ?: it.profile,
                            successMessage = "Profilo aggiornato"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun saveSpecialist(specialist: Specialist) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            updateSpecialistUseCase(specialist)
                .onSuccess {
                    val updated = (_uiState.value.profile as? UserProfile.SpecialistProfile)
                        ?.copy(specialist = specialist)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            profile = updated ?: it.profile,
                            successMessage = "Profilo aggiornato"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, showPasswordDialog = false) }
            changePasswordUseCase(currentPassword, newPassword)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, successMessage = "Password aggiornata") }
                }
                .onFailure { e ->
                    // Messaggio curato: stessa password → testo dedicato; policy → messaggio della
                    // regola; altro DomainError → suo testo; fallback neutro per il resto.
                    val message = (e as? DomainError)?.message ?: e.message ?: DomainError.Unknown.message
                    _uiState.update { it.copy(isSaving = false, error = message) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.update { it.copy(navigateToLogin = true) }
        }
    }

    fun deleteAccount(password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, showDeleteDialog = false) }
            deleteAccountUseCase(password)
                .onSuccess {
                    _uiState.update { it.copy(navigateToLogin = true) }
                }
                .onFailure { e ->
                    // Messaggio curato: password errata → "Credenziali non valide";
                    // specialista con pazienti → testo dedicato; altro → fallback neutro.
                    val message = (e as? DomainError)?.message ?: DomainError.Unknown.message
                    _uiState.update { it.copy(isSaving = false, error = message) }
                }
        }
    }

    fun showPasswordDialog() = _uiState.update { it.copy(showPasswordDialog = true) }
    fun hidePasswordDialog() = _uiState.update { it.copy(showPasswordDialog = false) }
    fun showDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = true) }
    fun hideDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = false) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccess() = _uiState.update { it.copy(successMessage = null) }
}