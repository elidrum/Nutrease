package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.BadgeStateRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import javax.inject.Inject

/**
 * Segna come viste tutte le richieste accettate del paziente: invocata all'apertura
 * della discovery specialisti, così il relativo badge in home si azzera al ritorno.
 */
class MarkAcceptedRequestsSeenUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val linkRequestRepository: LinkRequestRepository,
    private val badgeStateRepository: BadgeStateRepository
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val user = authRepository.getCurrentUser() ?: return@runCatching
        if (user.role != UserRole.PATIENT) return@runCatching
        val acceptedIds = linkRequestRepository.getSentRequests(user.taxCode).getOrThrow()
            .filter { it.status == LinkRequestStatus.ACCEPTED }
            .map { it.id }
            .toSet()
        badgeStateRepository.markAcceptedRequestsSeen(acceptedIds)
    }
}
