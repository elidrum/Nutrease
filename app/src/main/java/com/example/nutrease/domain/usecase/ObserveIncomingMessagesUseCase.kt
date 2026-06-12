package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Tick a ogni nuovo messaggio ricevuto in una qualsiasi chat dell'utente (RLS-scoped).
 * Le home lo osservano per ricalcolare i badge "chat non lette" in tempo reale, senza
 * dover lasciare e rientrare nella schermata. Da non confondere con
 * [ObserveNewMessagesUseCase], che emette i messaggi (col payload) di una singola chat.
 */
class ObserveIncomingMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<Unit> = chatRepository.observeNewMessages()
}
