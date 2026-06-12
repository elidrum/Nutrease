package com.example.nutrease.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.usecase.GetRecentMessagesUseCase
import com.example.nutrease.domain.usecase.MarkChatSeenUseCase
import com.example.nutrease.domain.usecase.ObserveNewMessagesUseCase
import com.example.nutrease.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val counterpartName: String = "",
    val currentUserUid: String? = null,
    val isLoading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    /** Evento transitorio: invio fallito → la UI mostra una snackbar. */
    val sendFailed: Boolean = false
)

/**
 * ViewModel della singola chat (RF23–RF24): carica lo storico, colleziona il Flow
 * Realtime dei nuovi messaggi e gestisce l'invio (single-flight). L'eco Realtime del
 * proprio insert è deduplicato per id, perché il messaggio inviato è già stato
 * appeso in modo ottimistico con i dati restituiti dal server.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getRecentMessagesUseCase: GetRecentMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val observeNewMessagesUseCase: ObserveNewMessagesUseCase,
    private val markChatSeenUseCase: MarkChatSeenUseCase,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: Long = savedStateHandle.get<Long>(ARG_CHAT_ID)
        ?: error("Missing argument $ARG_CHAT_ID")

    private val _uiState = MutableStateFlow(
        ChatUiState(counterpartName = savedStateHandle.get<String>(ARG_COUNTERPART_NAME).orEmpty())
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
        loadMessages()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id
            _uiState.update { it.copy(currentUserUid = uid) }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getRecentMessagesUseCase(chatId)
                .onSuccess { messages ->
                    _uiState.update { it.copy(isLoading = false, messages = messages) }
                    messages.lastOrNull()?.let { markSeen(it.createdAt) }
                    observeMessages()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private fun observeMessages() {
        observeNewMessagesUseCase(chatId)
            .onEach { appendMessage(it) }
            .launchIn(viewModelScope)
    }

    fun sendMessage(text: String) {
        if (_uiState.value.isSending) return // single-flight
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            sendMessageUseCase(chatId, text)
                .onSuccess { message ->
                    appendMessage(message) // optimistic: l'echo Realtime sarà deduplicato
                    _uiState.update { it.copy(isSending = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isSending = false, sendFailed = true) }
                }
        }
    }

    fun consumeSendError() = _uiState.update { it.copy(sendFailed = false) }

    /** Aggiunge un messaggio mantenendo l'ordine per `createdAt` e deduplicando per id. */
    private fun appendMessage(message: ChatMessage) {
        _uiState.update { state ->
            if (state.messages.any { it.id == message.id }) {
                state
            } else {
                state.copy(messages = (state.messages + message).sortedBy { it.createdAt })
            }
        }
        // L'utente sta guardando la chat: ciò che arriva (o che invia) è "letto".
        markSeen(message.createdAt)
    }

    /** Persiste il "visto" fino a [at] così il badge chat in home si azzera al ritorno. */
    private fun markSeen(at: Instant) {
        viewModelScope.launch { markChatSeenUseCase(chatId, at) }
    }

    companion object {
        const val ARG_CHAT_ID = "chat_id"
        const val ARG_COUNTERPART_NAME = "counterpart_name"
    }
}