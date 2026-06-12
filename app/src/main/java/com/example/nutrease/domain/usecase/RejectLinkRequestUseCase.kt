package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import javax.inject.Inject

/**
 * Rifiuto motivato di una richiesta (RF17): la motivazione è obbligatoria e limitata
 * a 500 caratteri. Le `require` qui rendono la regola valida per qualunque chiamante,
 * non solo per la dialog attuale.
 */
class RejectLinkRequestUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val linkRequestRepository: LinkRequestRepository
) {
    suspend operator fun invoke(requestId: Long, reason: String): Result<Unit> = runCatching {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.SPECIALIST) {
            throw IllegalStateException("Funzione disponibile solo per gli specialisti")
        }
        require(reason.isNotBlank()) { "La motivazione del rifiuto è obbligatoria" }
        require(reason.length <= MAX_REASON_LENGTH) {
            "La motivazione non può superare $MAX_REASON_LENGTH caratteri"
        }
        linkRequestRepository.rejectRequest(requestId, reason).getOrThrow()
    }

    companion object {
        const val MAX_REASON_LENGTH = 500
    }
}