package com.example.nutrease.ui.screens.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.LinkRequestWithPatient
import com.example.nutrease.domain.usecase.AcceptLinkRequestUseCase
import com.example.nutrease.domain.usecase.GetReceivedLinkRequestsUseCase
import com.example.nutrease.domain.usecase.RejectLinkRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinkRequestsUiState(
    val isLoading: Boolean = false,
    val requests: List<LinkRequestWithPatient> = emptyList(),
    val processingId: Long? = null,
    val error: String? = null,
    val rejectingRequest: LinkRequestWithPatient? = null,
    val toastMessage: ToastMessage? = null
) {
    enum class ToastMessage { ACCEPTED, REJECTED }
}

/**
 * ViewModel della inbox richieste dello specialista (RF15–RF17). `processingId`
 * implementa il single-flight: mentre una richiesta è in lavorazione le altre azioni
 * sono ignorate (niente doppi tap → doppie chiamate). On success la card sparisce
 * dalla lista locale, senza re-fetch.
 */
@HiltViewModel
class LinkRequestsViewModel @Inject constructor(
    private val getReceivedLinkRequestsUseCase: GetReceivedLinkRequestsUseCase,
    private val acceptLinkRequestUseCase: AcceptLinkRequestUseCase,
    private val rejectLinkRequestUseCase: RejectLinkRequestUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkRequestsUiState())
    val uiState: StateFlow<LinkRequestsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getReceivedLinkRequestsUseCase()
                .onSuccess { list ->
                    _uiState.update { it.copy(isLoading = false, requests = list) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun accept(request: LinkRequestWithPatient) {
        if (_uiState.value.processingId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(processingId = request.request.id, error = null) }
            acceptLinkRequestUseCase(request.request.id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            processingId = null,
                            requests = state.requests.filterNot { it.request.id == request.request.id },
                            toastMessage = LinkRequestsUiState.ToastMessage.ACCEPTED
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(processingId = null, error = e.message) }
                }
        }
    }

    fun startReject(request: LinkRequestWithPatient) {
        _uiState.update { it.copy(rejectingRequest = request) }
    }

    fun cancelReject() {
        _uiState.update { it.copy(rejectingRequest = null) }
    }

    fun confirmReject(reason: String) {
        val target = _uiState.value.rejectingRequest ?: return
        if (reason.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(processingId = target.request.id, rejectingRequest = null, error = null)
            }
            rejectLinkRequestUseCase(target.request.id, reason)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            processingId = null,
                            requests = state.requests.filterNot { it.request.id == target.request.id },
                            toastMessage = LinkRequestsUiState.ToastMessage.REJECTED
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(processingId = null, error = e.message) }
                }
        }
    }

    fun consumeToast() = _uiState.update { it.copy(toastMessage = null) }

    fun clearError() = _uiState.update { it.copy(error = null) }
}