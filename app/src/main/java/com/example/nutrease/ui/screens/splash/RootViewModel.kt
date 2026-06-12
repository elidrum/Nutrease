package com.example.nutrease.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.usecase.RescheduleRemindersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decide la destinazione iniziale all'avvio (RF3 "sessione persistita"): se
 * esiste una sessione Supabase valida, instrada alla home per ruolo, altrimenti
 * al login. `AuthRepository.getCurrentUser()` attende già il caricamento della
 * sessione persistita da storage.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val rescheduleRemindersUseCase: RescheduleRemindersUseCase
) : ViewModel() {

    sealed interface StartState {
        data object Loading : StartState
        data object Login : StartState
        data object PatientHome : StartState
        data object SpecialistHome : StartState
    }

    private val _state = MutableStateFlow<StartState>(StartState.Loading)
    val state: StateFlow<StartState> = _state.asStateFlow()

    init {
        resolveStart()
    }

    private fun resolveStart() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _state.value = when (user?.role) {
                UserRole.PATIENT -> StartState.PatientHome
                UserRole.SPECIALIST, UserRole.SECRETARY -> StartState.SpecialistHome
                null -> StartState.Login
            }
            // Riarma i promemoria esatti persi al riavvio/kill del dispositivo (no-op se non
            // paziente). Non blocca la navigazione: l'esito non influisce sulla start destination.
            if (user?.role == UserRole.PATIENT) {
                launch { rescheduleRemindersUseCase() }
            }
        }
    }
}