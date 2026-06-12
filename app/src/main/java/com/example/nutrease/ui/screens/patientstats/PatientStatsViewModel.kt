package com.example.nutrease.ui.screens.patientstats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.NutrientFilter
import com.example.nutrease.domain.model.PatientStats
import com.example.nutrease.domain.usecase.GetPatientStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import javax.inject.Inject

data class PatientStatsUiState(
    val patientName: String = "",
    val stats: PatientStats? = null,
    val selectedNutrient: NutrientFilter = NutrientFilter.LACTOSE,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel della dashboard statistiche (RF21), periodo fisso 30 giorni. Il fetch
 * avviene una volta all'avvio: il cambio chip nutriente aggiorna solo `selectedNutrient`
 * (le 4 serie sono già tutte in [PatientStats]). ALL non è selezionabile nel grafico.
 */
@HiltViewModel
class PatientStatsViewModel @Inject constructor(
    private val getPatientStatsUseCase: GetPatientStatsUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val fascicoloId: Int = savedStateHandle.get<Int>(ARG_FASCICOLO_ID)
        ?: error("Missing argument $ARG_FASCICOLO_ID")

    private val _uiState = MutableStateFlow(
        PatientStatsUiState(patientName = savedStateHandle.get<String>(ARG_PATIENT_NAME).orEmpty())
    )
    val uiState: StateFlow<PatientStatsUiState> = _uiState.asStateFlow()

    private var currentFetchJob: Job? = null

    init {
        fetch()
    }

    fun updateNutrient(filter: NutrientFilter) {
        if (filter == NutrientFilter.ALL) return
        _uiState.update { it.copy(selectedNutrient = filter) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun fetch() {
        currentFetchJob?.cancel()
        currentFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val today = currentDate()
            getPatientStatsUseCase(fascicoloId = fascicoloId, today = today)
                .onSuccess { stats ->
                    _uiState.update { it.copy(isLoading = false, stats = stats) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, stats = null, error = e.message)
                    }
                }
        }
    }

    private fun currentDate(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    companion object {
        const val ARG_FASCICOLO_ID = "fascicolo_id"
        const val ARG_PATIENT_NAME = "patient_name"
    }
}