package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Flow dei nuovi messaggi di UNA chat (RF24), via subscription Realtime: la ChatScreen
 * lo colleziona per aggiungere le bubble senza refresh. Da non confondere con
 * [ObserveIncomingMessagesUseCase], che emette un tick per QUALSIASI messaggio
 * dell'utente e serve solo ai badge delle home.
 */
class ObserveNewMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(chatId: Long): Flow<ChatMessage> =
        chatRepository.observeNewMessages(chatId)
}