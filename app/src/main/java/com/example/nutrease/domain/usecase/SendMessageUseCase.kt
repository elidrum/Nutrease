package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.domain.repository.ChatRepository
import javax.inject.Inject

/** Invio di un messaggio in chat (RF23). */
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    /**
     * Valida il testo (non vuoto dopo trim, max [MAX_MESSAGE_LENGTH] caratteri)
     * e delega l'invio al repository con il testo già trimmato.
     */
    suspend operator fun invoke(chatId: Long, text: String): Result<ChatMessage> = runCatching {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Il messaggio non può essere vuoto" }
        require(trimmed.length <= MAX_MESSAGE_LENGTH) {
            "Il messaggio non può superare $MAX_MESSAGE_LENGTH caratteri"
        }
        chatRepository.sendMessage(chatId, trimmed).getOrThrow()
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 2000
    }
}