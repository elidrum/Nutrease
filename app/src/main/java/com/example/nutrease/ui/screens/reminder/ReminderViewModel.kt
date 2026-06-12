package com.example.nutrease.ui.screens.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.ReminderConfig
import com.example.nutrease.domain.usecase.DeleteReminderUseCase
import com.example.nutrease.domain.usecase.GetReminderConfigsUseCase
import com.example.nutrease.domain.usecase.SaveReminderConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import javax.inject.Inject

/** Stato dell'editor (dialog) di un singolo promemoria. `id == null` ⇒ nuovo. */
/** Stato della dialog editor: id nullo = nuovo promemoria, valorizzato = modifica. */
data class ReminderEditorState(
    val id: Long? = null,
    val enabled: Boolean = true,
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val time: LocalTime = LocalTime(20, 0),
    val message: String = ""
)

data class ReminderUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val reminders: List<ReminderConfig> = emptyList(),
    val editor: ReminderEditorState? = null,
    val savedSnackbar: Boolean = false,
    val error: String? = null
) {
    val hasEnabledReminder: Boolean get() = reminders.any { it.enabled }
}

/**
 * ViewModel dei promemoria (RF22): lista + dialog editor. Ogni salvataggio/toggle/
 * eliminazione passa dagli UseCase, che oltre al DB riallineano gli allarmi locali
 * (cancel + schedule): il ViewModel non vede mai AlarmManager.
 */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val getReminderConfigs: GetReminderConfigsUseCase,
    private val saveReminderConfig: SaveReminderConfigUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderUiState(isLoading = true))
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    // --- Editor ---------------------------------------------------------------

    fun openNewReminder() {
        _uiState.update { it.copy(editor = ReminderEditorState()) }
    }

    fun openEditReminder(config: ReminderConfig) {
        _uiState.update {
            it.copy(
                editor = ReminderEditorState(
                    id = config.id,
                    enabled = config.enabled,
                    daysOfWeek = config.daysOfWeek,
                    time = config.time,
                    message = config.message.orEmpty()
                )
            )
        }
    }

    fun closeEditor() = _uiState.update { it.copy(editor = null) }

    fun editorToggleEnabled(value: Boolean) = updateEditor { it.copy(enabled = value) }

    fun editorToggleDay(day: DayOfWeek) = updateEditor { editor ->
        val next = if (day in editor.daysOfWeek) editor.daysOfWeek - day else editor.daysOfWeek + day
        editor.copy(daysOfWeek = next)
    }

    fun editorUpdateTime(time: LocalTime) = updateEditor { it.copy(time = time) }

    fun editorUpdateMessage(text: String) =
        updateEditor { it.copy(message = text.take(ReminderConfig.MAX_MESSAGE_LENGTH)) }

    private fun updateEditor(transform: (ReminderEditorState) -> ReminderEditorState) {
        _uiState.update { state -> state.editor?.let { state.copy(editor = transform(it)) } ?: state }
    }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        if (editor.daysOfWeek.isEmpty()) {
            _uiState.update { it.copy(error = "Seleziona almeno un giorno della settimana") }
            return
        }
        val config = ReminderConfig(
            id = editor.id,
            patientTaxCode = "",
            enabled = editor.enabled,
            daysOfWeek = editor.daysOfWeek,
            time = editor.time,
            message = editor.message.trim().ifBlank { null }
        )
        runMutation {
            saveReminderConfig(config).getOrThrow()
            _uiState.update { it.copy(editor = null) }
        }
    }

    // --- Azioni sulla lista ---------------------------------------------------

    fun toggleReminderEnabled(config: ReminderConfig) {
        runMutation { saveReminderConfig(config.copy(enabled = !config.enabled)).getOrThrow() }
    }

    fun deleteReminder(configId: Long) {
        runMutation { deleteReminderUseCase(configId).getOrThrow() }
    }

    fun addMealPresets() {
        runMutation {
            ReminderConfig.mealPresets(patientTaxCode = "").forEach { preset ->
                saveReminderConfig(preset).getOrThrow()
            }
        }
    }

    fun consumeSnackbar() = _uiState.update { it.copy(savedSnackbar = false) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    /** Esegue una mutazione, poi ricarica la lista e segnala il salvataggio. */
    private fun runMutation(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching { block() }
                .onSuccess {
                    reload()
                    _uiState.update { it.copy(isSaving = false, savedSnackbar = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getReminderConfigs()
                .onSuccess { configs ->
                    _uiState.update { it.copy(isLoading = false, reminders = configs) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /** Ricarica la lista senza toccare il flag di loading a schermo intero. */
    private suspend fun reload() {
        getReminderConfigs()
            .onSuccess { configs -> _uiState.update { it.copy(reminders = configs) } }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }
}
