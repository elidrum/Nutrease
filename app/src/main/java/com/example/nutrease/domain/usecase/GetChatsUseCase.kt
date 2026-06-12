package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.ChatPreview
import com.example.nutrease.domain.repository.ChatRepository
import javax.inject.Inject

/** Lista chat dell'utente loggato con anteprima ultimo messaggio, già ordinata (RF23). */
class GetChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(): Result<List<ChatPreview>> =
        chatRepository.getChatsForCurrentUser()
}