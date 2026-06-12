package com.example.nutrease.ui.screens.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.usecase.DeleteMealUseCase
import com.example.nutrease.domain.usecase.DeleteSymptomUseCase
import com.example.nutrease.domain.usecase.GetMealsForDateUseCase
import com.example.nutrease.domain.usecase.GetPatientFascicoloUseCase
import com.example.nutrease.domain.usecase.GetSymptomsForDateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

/** Stato della schermata diario: giorno selezionato + pasti e sintomi di quel giorno. */
data class DiaryUiState(
    val isLoading: Boolean = true,
    val selectedDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val fascicoloId: Int? = null,
    val meals: List<Meal> = emptyList(),
    val symptoms: List<Symptom> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * ViewModel del diario giornaliero (RF11–RF12). All'avvio risolve il fascicolo attivo
 * (prerequisito: senza collegamento a uno specialista il diario mostra errore), poi
 * carica pasti+sintomi del giorno. Il refresh è invocato dalla screen anche al ritorno
 * da add/edit (via lifecycle), così la lista è sempre aggiornata senza passare risultati
 * di navigazione.
 */
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val getPatientFascicoloUseCase: GetPatientFascicoloUseCase,
    private val getMealsForDateUseCase: GetMealsForDateUseCase,
    private val getSymptomsForDateUseCase: GetSymptomsForDateUseCase,
    private val deleteMealUseCase: DeleteMealUseCase,
    private val deleteSymptomUseCase: DeleteSymptomUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    init {
        loadFascicoloAndDay()
    }

    fun selectDate(date: LocalDate) {
        if (date == _uiState.value.selectedDate) return
        _uiState.update { it.copy(selectedDate = date) }
        refreshDay()
    }

    fun refresh() = refreshDay()

    fun deleteMeal(mealId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            deleteMealUseCase(mealId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Pasto eliminato") }
                    refreshDay()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun deleteSymptom(symptomId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            deleteSymptomUseCase(symptomId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Sintomo eliminato") }
                    refreshDay()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccess() = _uiState.update { it.copy(successMessage = null) }

    private fun loadFascicoloAndDay() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getPatientFascicoloUseCase()
                .onSuccess { fascicoloId ->
                    _uiState.update { it.copy(fascicoloId = fascicoloId) }
                    refreshDay()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /** Ricarica pasti e sintomi del giorno selezionato; no-op se il fascicolo non è ancora noto. */
    private fun refreshDay() {
        val fascicoloId = _uiState.value.fascicoloId ?: return
        val date = _uiState.value.selectedDate
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val mealsResult = getMealsForDateUseCase(fascicoloId, date)
            val symptomsResult = getSymptomsForDateUseCase(fascicoloId, date)
            val meals = mealsResult.getOrElse {
                _uiState.update { s -> s.copy(isLoading = false, error = it.message) }
                return@launch
            }
            val symptoms = symptomsResult.getOrElse {
                _uiState.update { s -> s.copy(isLoading = false, error = it.message) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = false, meals = meals, symptoms = symptoms) }
        }
    }
}