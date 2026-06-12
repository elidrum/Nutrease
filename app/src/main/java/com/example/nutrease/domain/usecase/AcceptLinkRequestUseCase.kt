package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import javax.inject.Inject

/**
 * Accettazione di una richiesta (RF16). Il client cambia solo lo stato: fascicolo
 * clinico e chat nascono da un trigger sul DB, nella stessa transazione.
 */
class AcceptLinkRequestUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val linkRequestRepository: LinkRequestRepository
) {
    suspend operator fun invoke(requestId: Long): Result<Unit> = runCatching {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.SPECIALIST) {
            throw IllegalStateException("Funzione disponibile solo per gli specialisti")
        }
        linkRequestRepository.acceptRequest(requestId).getOrThrow()
    }
}