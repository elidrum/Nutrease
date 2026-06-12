package com.example.nutrease.ui.screens.linkedpatients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.LinkedPatient
import com.example.nutrease.domain.usecase.GetLinkedPatientsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinkedPatientsUiState(
    val isLoading: Boolean = false,
    val patients: List<LinkedPatient> = emptyList(),
    val error: String? = null
)

/** ViewModel della lista "I miei pazienti" dello specialista (RF18): solo fetch + refresh. */
@HiltViewModel
class LinkedPatientsViewModel @Inject constructor(
    private val getLinkedPatientsUseCase: GetLinkedPatientsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkedPatientsUiState())
    val uiState: StateFlow<LinkedPatientsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getLinkedPatientsUseCase()
                .onSuccess { list ->
                    _uiState.update { it.copy(isLoading = false, patients = list) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}