package com.example.nutrease.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.UserProfile
import com.example.nutrease.domain.usecase.GetProfileUseCase
import com.example.nutrease.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String? = null,
    val navigateToLogin: Boolean = false
)

/**
 * ViewModel condiviso dalle due home (paziente e specialista) per ciò che hanno in
 * comune: saluto col nome dell'utente e logout (RF4). I conteggi dei badge stanno
 * nei ViewModel specifici (PatientHomeViewModel / SpecialistHomeViewModel).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val getProfileUseCase: GetProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserName()
    }

    private fun loadUserName() {
        viewModelScope.launch {
            getProfileUseCase().onSuccess { profile ->
                val name = when (profile) {
                    is UserProfile.PatientProfile -> profile.patient.firstName
                    is UserProfile.SpecialistProfile -> profile.specialist.firstName
                }
                _uiState.update { it.copy(userName = name) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.update { it.copy(navigateToLogin = true) }
        }
    }
}