package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.BadgeStateRepository
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Segna i messaggi di una chat come letti fino a `lastMessageAt` (l'ultimo messaggio
 * mostrato all'utente). Invocata dalla ChatScreen all'apertura e a ogni nuovo messaggio.
 */
class MarkChatSeenUseCase @Inject constructor(
    private val badgeStateRepository: BadgeStateRepository
) {
    suspend operator fun invoke(chatId: Long, lastMessageAt: Instant) {
        badgeStateRepository.setChatLastSeen(chatId, lastMessageAt)
    }
}
