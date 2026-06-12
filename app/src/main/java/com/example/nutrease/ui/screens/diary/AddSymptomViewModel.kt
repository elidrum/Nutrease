package com.example.nutrease.ui.screens.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.model.SymptomType
import com.example.nutrease.domain.usecase.AddSymptomUseCase
import com.example.nutrease.domain.usecase.GetPatientFascicoloUseCase
import com.example.nutrease.domain.usecase.GetSymptomUseCase
import com.example.nutrease.domain.usecase.UpdateSymptomUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Clock

data class AddSymptomUiState(
    val isEditing: Boolean = false,
    val isLoadingExisting: Boolean = false,
    val date: LocalDate = todayLocal(),
    val time: LocalTime = nowLocalTime(),
    val type: SymptomType = SymptomType.BLOATING,
    val severity: SymptomSeverity = SymptomSeverity.MILD,
    // Testo libero usato solo quando type == OTHER (vedi save()/dbDescription nel repo).
    val note: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

const val ARG_SYMPTOM_ID = "symptom_id"

/**
 * ViewModel del form sintomo (RF10/RF12), doppia modalità: la rotta porta
 * `symptom_id` (>0 = modifica, da precaricare; 0/assente = inserimento). L'id arriva
 * via [SavedStateHandle], così la modalità sopravvive anche alla process death.
 */
@HiltViewModel
class AddSymptomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPatientFascicoloUseCase: GetPatientFascicoloUseCase,
    private val getSymptomUseCase: GetSymptomUseCase,
    private val addSymptomUseCase: AddSymptomUseCase,
    private val updateSymptomUseCase: UpdateSymptomUseCase
) : ViewModel() {

    private val editingSymptomId: Long? =
        savedStateHandle.get<Long>(ARG_SYMPTOM_ID)?.takeIf { it > 0L }
    private var fascicoloIdForEdit: Int? = null

    private val _uiState = MutableStateFlow(
        AddSymptomUiState(
            isEditing = editingSymptomId != null,
            isLoadingExisting = editingSymptomId != null
        )
    )
    val uiState: StateFlow<AddSymptomUiState> = _uiState.asStateFlow()

    init {
        editingSymptomId?.let { loadExistingSymptom(it) }
    }

    /** Pre-imposta la data dal diario (solo in inserimento: in modifica vince quella salvata). */
    fun setInitialDate(date: LocalDate) {
        if (_uiState.value.isEditing) return
        _uiState.update { it.copy(date = date) }
    }

    fun updateDate(date: LocalDate) = _uiState.update { it.copy(date = date) }
    fun updateTime(time: LocalTime) = _uiState.update { it.copy(time = time) }
    fun updateType(type: SymptomType) = _uiState.update { it.copy(type = type) }
    fun updateSeverity(severity: SymptomSeverity) = _uiState.update { it.copy(severity = severity) }
    fun updateNote(text: String) = _uiState.update { it.copy(note = text) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun save() {
        val state = _uiState.value
        // Non si registra un sintomo in un momento futuro (RF12): data+ora oltre "adesso".
        if (isFutureDateTime(state.date, state.time)) {
            _uiState.update { it.copy(error = "Non puoi registrare un sintomo in una data o un'ora futura") }
            return
        }
        // Tipo "Altro": la descrizione libera è obbligatoria, altrimenti non sapremmo quale sintomo.
        if (state.type == SymptomType.OTHER && state.note.isBlank()) {
            _uiState.update { it.copy(error = "Descrivi il sintomo nel campo \"Altro\"") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val fascicoloId = fascicoloIdForEdit ?: getPatientFascicoloUseCase().getOrElse {
                _uiState.update { s -> s.copy(isSaving = false, error = it.message) }
                return@launch
            }
            val symptom = Symptom(
                id = editingSymptomId,
                fascicoloId = fascicoloId,
                date = state.date,
                time = state.time,
                type = state.type,
                severity = state.severity,
                note = state.note.takeIf { state.type == SymptomType.OTHER }?.trim()?.ifBlank { null }
            )
            val result = if (editingSymptomId != null) {
                updateSymptomUseCase(symptom)
            } else {
                addSymptomUseCase(symptom).map { Unit }
            }
            result
                .onSuccess { _uiState.update { s -> s.copy(isSaving = false, saved = true) } }
                .onFailure { e -> _uiState.update { s -> s.copy(isSaving = false, error = e.message) } }
        }
    }

    private fun loadExistingSymptom(id: Long) {
        viewModelScope.launch {
            getSymptomUseCase(id)
                .onSuccess { symptom ->
                    fascicoloIdForEdit = symptom.fascicoloId
                    _uiState.update {
                        it.copy(
                            isLoadingExisting = false,
                            date = symptom.date,
                            time = symptom.time,
                            type = symptom.type,
                            severity = symptom.severity,
                            note = symptom.note.orEmpty()
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingExisting = false, error = e.message) }
                }
        }
    }
}

/** True se la coppia data+ora scelta cade oltre l'istante attuale (fuso locale). */
private fun isFutureDateTime(date: LocalDate, time: LocalTime): Boolean {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalDateTime(date, time) > now
}

private fun todayLocal(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun nowLocalTime(): LocalTime {
    val dt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalTime(dt.hour, dt.minute)
}