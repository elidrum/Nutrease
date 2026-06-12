package com.example.nutrease.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.usecase.GetLinkedSpecialistUseCase
import com.example.nutrease.domain.usecase.GetUnreadChatsCountUseCase
import com.example.nutrease.domain.usecase.GetUnseenAcceptedRequestsCountUseCase
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

data class PatientHomeUiState(
    val unreadChatsCount: Int = 0,
    val acceptedRequestsCount: Int = 0,
    // null + loaded=false: non ancora caricato (nessuna card); null + loaded=true: nessun collegamento.
    val linkedSpecialist: Specialist? = null,
    val isLinkedSpecialistLoaded: Boolean = false
)

/**
 * Badge della home paziente (chat non lette, richieste accettate non viste) +
 * specialista collegato (card "Il tuo specialista"). Affiancato a [HomeViewModel]
 * (nome/logout), come per lo specialista.
 */
@HiltViewModel
class PatientHomeViewModel @Inject constructor(
    private val getUnreadChatsCountUseCase: GetUnreadChatsCountUseCase,
    private val getUnseenAcceptedRequestsCountUseCase: GetUnseenAcceptedRequestsCountUseCase,
    private val getLinkedSpecialistUseCase: GetLinkedSpecialistUseCase,
    // Solo per l'osservazione in init: parametro semplice, non property (evita il warning
    // "constructor parameter is never used as a property").
    observeIncomingMessagesUseCase: ObserveIncomingMessagesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientHomeUiState())
    val uiState: StateFlow<PatientHomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
        // Aggiornamento live: a ogni nuovo messaggio (Realtime) ricalcola i SOLI badge, anche
        // mentre l'utente è fermo sulla home (LifecycleStartEffect copre solo i ritorni).
        // Lo specialista collegato cambia solo all'accettazione di una richiesta: rifetcharlo
        // per ogni messaggio sarebbe I/O sprecato (stessa separazione di SpecialistHomeViewModel).
        observeIncomingMessagesUseCase()
            .onEach { refreshBadges() }
            .launchIn(viewModelScope)
    }

    /** Ricalcola badge e specialista collegato: invocata all'avvio e a ogni ritorno in home. */
    fun refresh() {
        refreshBadges()
        viewModelScope.launch {
            getLinkedSpecialistUseCase().onSuccess { specialist ->
                _uiState.update {
                    it.copy(linkedSpecialist = specialist, isLinkedSpecialistLoaded = true)
                }
            }
        }
    }

    private fun refreshBadges() {
        viewModelScope.launch {
            getUnreadChatsCountUseCase().onSuccess { count ->
                _uiState.update { it.copy(unreadChatsCount = count) }
            }
        }
        viewModelScope.launch {
            getUnseenAcceptedRequestsCountUseCase().onSuccess { count ->
                _uiState.update { it.copy(acceptedRequestsCount = count) }
            }
        }
    }
}
