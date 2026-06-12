package com.example.nutrease.ui.screens.patientdiary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.DiaryDateRange
import com.example.nutrease.domain.model.NutrientFilter
import com.example.nutrease.domain.model.NutrientTotals
import com.example.nutrease.domain.model.PatientDiaryDay
import com.example.nutrease.domain.usecase.GetPatientDiaryRangeUseCase
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

data class PatientDiaryUiState(
    val fascicoloId: Int = 0,
    val patientName: String = "",
    val range: DiaryDateRange = DiaryDateRange.Last7Days,
    val nutrientFilter: NutrientFilter = NutrientFilter.ALL,
    val days: List<PatientDiaryDay> = emptyList(),
    val periodTotals: NutrientTotals = NutrientTotals.Zero,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel del diario paziente lato specialista (RF19–RF20). Fascicolo e nome
 * arrivano dalla rotta via [SavedStateHandle]. I due filtri hanno costi diversi e si
 * comportano di conseguenza: cambiare periodo cancella l'eventuale fetch in corso
 * (`Job.cancel`) e ne lancia uno nuovo; cambiare nutriente è solo un update di stato
 * (la UI evidenzia, non si rifiltra nulla).
 */
@HiltViewModel
class PatientDiaryViewModel @Inject constructor(
    private val getPatientDiaryRangeUseCase: GetPatientDiaryRangeUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val fascicoloId: Int = savedStateHandle.get<Int>(ARG_FASCICOLO_ID)
        ?: error("Missing argument $ARG_FASCICOLO_ID")

    private val _uiState = MutableStateFlow(
        PatientDiaryUiState(
            fascicoloId = fascicoloId,
            patientName = savedStateHandle.get<String>(ARG_PATIENT_NAME).orEmpty()
        )
    )
    val uiState: StateFlow<PatientDiaryUiState> = _uiState.asStateFlow()

    private var currentFetchJob: Job? = null

    init {
        fetchDiary(_uiState.value.range)
    }

    fun updateRange(range: DiaryDateRange) {
        if (range == _uiState.value.range) return
        _uiState.update { it.copy(range = range) }
        fetchDiary(range)
    }

    fun updateNutrientFilter(filter: NutrientFilter) {
        _uiState.update { it.copy(nutrientFilter = filter) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun fetchDiary(range: DiaryDateRange) {
        currentFetchJob?.cancel()
        currentFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val today = currentDate()
            getPatientDiaryRangeUseCase(
                fascicoloId = fascicoloId,
                range = range,
                today = today
            )
                .onSuccess { days ->
                    val totals = days.fold(NutrientTotals.Zero) { acc, d -> acc + d.totals }
                    _uiState.update {
                        it.copy(isLoading = false, days = days, periodTotals = totals)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            days = emptyList(),
                            periodTotals = NutrientTotals.Zero,
                            error = e.message
                        )
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