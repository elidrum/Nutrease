package com.example.nutrease.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.AgePolicy
import com.example.nutrease.domain.model.Gender
import com.example.nutrease.domain.model.PasswordPolicy
import com.example.nutrease.domain.model.RegisterData
import com.example.nutrease.domain.model.SpecializationType
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Clock

data class RegisterUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val navigateTo: UserRole? = null
)

/**
 * ViewModel della registrazione (RF1/RF2). Le validazioni di forma (campi vuoti,
 * lunghezze CF/P.IVA) vivono qui perché sono regole del form; quelle di dominio
 * riusabili (password, età minima) sono delegate a [PasswordPolicy] e [AgePolicy].
 * Tutto avviene PRIMA della chiamata di rete: feedback immediato e zero richieste inutili.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun registerPatient(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        taxCode: String,
        gender: Gender,
        birthDate: LocalDate
    ) {
        if (!validate(email, password, firstName, lastName, taxCode)) return

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        AgePolicy.validate(birthDate, today)?.let { ageError ->
            _uiState.update { it.copy(error = ageError.message) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            registerUseCase(
                RegisterData.PatientData(
                    email = email.trim(),
                    password = password,
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    taxCode = taxCode.trim().uppercase(),
                    gender = gender,
                    birthDate = birthDate
                )
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, navigateTo = UserRole.PATIENT) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Errore durante la registrazione") }
            }
        }
    }

    fun registerSpecialist(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        taxCode: String,
        vatNumber: String,
        specialization: SpecializationType,
        city: String
    ) {
        if (!validate(email, password, firstName, lastName, taxCode)) return
        if (vatNumber.trim().length != 11) {
            _uiState.update { it.copy(error = "La Partita IVA deve essere di 11 cifre") }
            return
        }
        if (city.isBlank()) {
            _uiState.update { it.copy(error = "Inserisci la città") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            registerUseCase(
                RegisterData.SpecialistData(
                    email = email.trim(),
                    password = password,
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    taxCode = taxCode.trim().uppercase(),
                    vatNumber = vatNumber.trim(),
                    specialization = specialization,
                    city = city.trim()
                )
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, navigateTo = UserRole.SPECIALIST) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Errore durante la registrazione") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun validate(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        taxCode: String
    ): Boolean {
        val error = when {
            firstName.isBlank() -> "Inserisci il nome"
            lastName.isBlank() -> "Inserisci il cognome"
            taxCode.trim().length != 16 -> "Il Codice Fiscale deve essere di 16 caratteri"
            email.isBlank() || !email.contains("@") -> "Inserisci un'email valida"
            else -> PasswordPolicy.validate(password)?.message
        }
        if (error != null) _uiState.update { it.copy(error = error) }
        return error == null
    }
}