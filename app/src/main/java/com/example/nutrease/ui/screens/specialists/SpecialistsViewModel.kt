package com.example.nutrease.ui.screens.specialists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.SpecializationType
import com.example.nutrease.domain.usecase.GetLinkedSpecialistUseCase
import com.example.nutrease.domain.usecase.MarkAcceptedRequestsSeenUseCase
import com.example.nutrease.domain.usecase.SearchSpecialistsUseCase
import com.example.nutrease.domain.usecase.SendLinkRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpecialistsUiState(
    val query: String = "",
    val specializationFilter: SpecializationType? = null,
    val cityFilter: String = "",
    val specialists: List<Specialist> = emptyList(),
    val isLoadingPage: Boolean = false,
    val isAppending: Boolean = false,
    val page: Int = 0,
    val canLoadMore: Boolean = true,
    val error: String? = null,
    val pendingLinkRequestFor: Specialist? = null,
    val isSendingLinkRequest: Boolean = false,
    val linkRequestSent: Boolean = false,
    // Nome dello specialista già collegato: alimenta l'avviso "una nuova richiesta lo sostituirà".
    val linkedSpecialistName: String? = null
)

/**
 * ViewModel della discovery specialisti (RF13) + invio richiesta via dialog (RF14).
 *
 * La ricerca è una catena reattiva: i tre filtri vivono in tre `MutableStateFlow`
 * combinati con `combine`; i campi testuali (query, città) passano per `debounce(300)`
 * (niente query a ogni tasto, si aspetta una pausa di digitazione) mentre il dropdown
 * specializzazione no (un click = ricerca immediata); `distinctUntilChanged` evita
 * ricerche duplicate a parità di filtri. La paginazione è un infinite-scroll:
 * `loadMore` appende la pagina successiva finché il server riempie pagine intere.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SpecialistsViewModel @Inject constructor(
    private val searchSpecialistsUseCase: SearchSpecialistsUseCase,
    private val sendLinkRequestUseCase: SendLinkRequestUseCase,
    private val markAcceptedRequestsSeenUseCase: MarkAcceptedRequestsSeenUseCase,
    private val getLinkedSpecialistUseCase: GetLinkedSpecialistUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpecialistsUiState())
    val uiState: StateFlow<SpecialistsUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private val specializationFlow = MutableStateFlow<SpecializationType?>(null)
    private val cityFlow = MutableStateFlow("")
    private var observerJob: Job? = null

    init {
        observeFilters()
        // Aprendo la discovery il paziente "vede" le richieste accettate → azzera il badge.
        viewModelScope.launch { markAcceptedRequestsSeenUseCase() }
        loadLinkedSpecialist()
    }

    /**
     * Se esiste già un collegamento attivo, la UI avvisa che una nuova richiesta lo
     * sostituirà (banner + riga nel dialog). Ri-tentata in [startLinkRequest] se il
     * primo fetch è fallito (es. rete): l'avviso è di sicurezza, non deve sparire
     * in silenzio per un errore transitorio all'init.
     */
    private fun loadLinkedSpecialist() {
        viewModelScope.launch {
            getLinkedSpecialistUseCase().onSuccess { linked ->
                _uiState.update {
                    it.copy(linkedSpecialistName = linked?.let { s -> "${s.firstName} ${s.lastName}" })
                }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun updateSpecialization(specialization: SpecializationType?) {
        _uiState.update { it.copy(specializationFilter = specialization) }
        specializationFlow.value = specialization
    }

    fun updateCity(city: String) {
        _uiState.update { it.copy(cityFilter = city) }
        cityFlow.value = city
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingPage || state.isAppending || !state.canLoadMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAppending = true, error = null) }
            val nextPage = state.page + 1
            searchSpecialistsUseCase(
                query = state.query,
                page = nextPage,
                specialization = state.specializationFilter,
                city = state.cityFilter
            )
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(
                            isAppending = false,
                            specialists = it.specialists + results,
                            page = nextPage,
                            canLoadMore = results.size >= SearchSpecialistsUseCase.DEFAULT_PAGE_SIZE
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isAppending = false, error = e.message) }
                }
        }
    }

    fun startLinkRequest(specialist: Specialist) {
        // Best-effort: se l'avviso di sostituzione manca per un fetch fallito, riprova
        // prima che il dialog confermi l'azione.
        if (_uiState.value.linkedSpecialistName == null) loadLinkedSpecialist()
        _uiState.update { it.copy(pendingLinkRequestFor = specialist, linkRequestSent = false) }
    }

    fun cancelLinkRequest() {
        _uiState.update { it.copy(pendingLinkRequestFor = null) }
    }

    /** Invia la richiesta allo specialista in dialog; on success la card sparisce dalla lista. */
    fun confirmLinkRequest(message: String?) {
        val target = _uiState.value.pendingLinkRequestFor ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingLinkRequest = true) }
            sendLinkRequestUseCase(
                specialistTaxCode = target.taxCode,
                message = message?.trim()?.takeIf { it.isNotBlank() }
            )
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSendingLinkRequest = false,
                            pendingLinkRequestFor = null,
                            linkRequestSent = true,
                            specialists = it.specialists.filterNot { s -> s.taxCode == target.taxCode }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSendingLinkRequest = false,
                            pendingLinkRequestFor = null,
                            error = e.message
                        )
                    }
                }
        }
    }

    fun consumeLinkRequestSent() = _uiState.update { it.copy(linkRequestSent = false) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun observeFilters() {
        observerJob = viewModelScope.launch {
            combine(
                queryFlow.debounce(300),
                specializationFlow,
                cityFlow.debounce(300)
            ) { q, s, c -> Triple(q, s, c) }
                .distinctUntilChanged()
                .collect { (q, s, c) -> runFreshSearch(q, s, c) }
        }
    }

    private suspend fun runFreshSearch(
        query: String,
        specialization: SpecializationType?,
        city: String
    ) {
        _uiState.update { it.copy(isLoadingPage = true, error = null) }
        searchSpecialistsUseCase(
            query = query,
            page = 0,
            specialization = specialization,
            city = city
        )
            .onSuccess { results ->
                _uiState.update {
                    it.copy(
                        isLoadingPage = false,
                        specialists = results,
                        page = 0,
                        canLoadMore = results.size >= SearchSpecialistsUseCase.DEFAULT_PAGE_SIZE
                    )
                }
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(isLoadingPage = false, specialists = emptyList(), error = e.message)
                }
            }
    }
}