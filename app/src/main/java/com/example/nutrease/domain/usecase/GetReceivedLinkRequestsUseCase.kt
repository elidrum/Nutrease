package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.LinkRequestWithPatient
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import javax.inject.Inject

/**
 * Richieste ricevute dallo specialista loggato (RF15), di default le pendenti
 * (parametro `status` per il conteggio badge e usi futuri). Solo per specialisti.
 */
class GetReceivedLinkRequestsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val linkRequestRepository: LinkRequestRepository
) {
    suspend operator fun invoke(
        status: LinkRequestStatus = LinkRequestStatus.PENDING
    ): Result<List<LinkRequestWithPatient>> = runCatching {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.SPECIALIST) {
            throw IllegalStateException("Funzione disponibile solo per gli specialisti")
        }
        linkRequestRepository.getReceivedRequests(
            specialistTaxCode = authUser.taxCode,
            status = status
        ).getOrThrow()
    }
}