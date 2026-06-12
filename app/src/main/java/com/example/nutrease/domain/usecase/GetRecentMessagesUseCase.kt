package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * Storico iniziale di una chat: gli ultimi `limit` messaggi in ordine cronologico.
 * I successivi arrivano in tempo reale via [ObserveNewMessagesUseCase].
 */
class GetRecentMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: Long, limit: Int = 50): Result<List<ChatMessage>> =
        chatRepository.getRecentMessages(chatId, limit)
}