package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.BadgeStateRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import javax.inject.Inject

/**
 * Numero di richieste di collegamento inviate dal paziente che risultano "Accettata"
 * ma non ancora viste. Il badge si azzera quando il paziente apre la discovery
 * specialisti ([MarkAcceptedRequestsSeenUseCase]). Per ruoli non-paziente ritorna 0.
 */
class GetUnseenAcceptedRequestsCountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val linkRequestRepository: LinkRequestRepository,
    private val badgeStateRepository: BadgeStateRepository
) {
    suspend operator fun invoke(): Result<Int> = runCatching {
        val user = authRepository.getCurrentUser() ?: return@runCatching 0
        if (user.role != UserRole.PATIENT) return@runCatching 0
        val acceptedIds = linkRequestRepository.getSentRequests(user.taxCode).getOrThrow()
            .filter { it.status == LinkRequestStatus.ACCEPTED }
            .map { it.id }
            .toSet()
        (acceptedIds - badgeStateRepository.seenAcceptedRequestIds()).size
    }
}
