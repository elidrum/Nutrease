package com.example.nutrease.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.UserProfile
import com.example.nutrease.domain.usecase.GetProfileUseCase
import com.example.nutrease.domain.usecase.GetReceivedLinkRequestsUseCase
import com.example.nutrease.domain.usecase.GetUnreadChatsCountUseCase
import com.example.nutrease.domain.usecase.ObserveIncomingMessagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpecialistHomeUiState(
    val pendingRequestsCount: Int = 0,
    val unreadChatsCount: Int = 0,
    // True quando lo specialista è registrato ma non ancora approvato da un admin.
    val isPendingVerification: Boolean = false
)

/**
 * ViewModel della home specialista: badge richieste pendenti e chat non lette
 * (quest'ultimo aggiornato live osservando il Flow Realtime dei messaggi in arrivo)
 * più lo stato di verifica dell'account.
 */
@HiltViewModel
class SpecialistHomeViewModel @Inject constructor(
    private val getReceivedLinkRequestsUseCase: GetReceivedLinkRequestsUseCase,
    private val getUnreadChatsCountUseCase: GetUnreadChatsCountUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    // Solo per l'osservazione in init: parametro semplice, non property (evita il warning
    // "constructor parameter is never used as a property").
    observeIncomingMessagesUseCase: ObserveIncomingMessagesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpecialistHomeUiState())
    val uiState: StateFlow<SpecialistHomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
        // Aggiornamento live del badge "chat non lette": a ogni nuovo messaggio (Realtime)
        // ricalcola, anche se lo specialista è fermo sulla home.
        observeIncomingMessagesUseCase()
            .onEach { refreshUnreadChats() }
            .launchIn(viewModelScope)
    }

    /** Ricalcola badge + stato di verifica: invocata all'avvio e a ogni ritorno in home. */
    fun refresh() {
        refreshPending()
        refreshUnreadChats()
        refreshVerification()
    }

    /**
     * Recupera lo stato di approvazione admin. In caso di errore lascia invariato lo
     * stato (default non-pending): un guasto di rete non deve mostrare un falso "in verifica".
     */
    fun refreshVerification() {
        viewModelScope.launch {
            getProfileUseCase()
                .onSuccess { profile ->
                    val pending = (profile as? UserProfile.SpecialistProfile)
                        ?.specialist?.isVerified == false
                    _uiState.update { it.copy(isPendingVerification = pending) }
                }
        }
    }

    fun refreshPending() {
        viewModelScope.launch {
            getReceivedLinkRequestsUseCase()
                .onSuccess { list ->
                    _uiState.update { it.copy(pendingRequestsCount = list.size) }
                }
        }
    }

    fun refreshUnreadChats() {
        viewModelScope.launch {
            getUnreadChatsCountUseCase()
                .onSuccess { count ->
                    _uiState.update { it.copy(unreadChatsCount = count) }
                }
        }
    }
}