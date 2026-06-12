package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import javax.inject.Inject

/**
 * Invio richiesta di collegamento a uno specialista (RF14). Regole qui: solo il
 * paziente invia, il messaggio opzionale è trimmato e limitato a 500 caratteri
 * (stesso limite della colonna DB). Il codice fiscale del mittente viene dalla
 * sessione, mai dalla UI.
 */
class SendLinkRequestUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val linkRequestRepository: LinkRequestRepository
) {
    suspend operator fun invoke(
        specialistTaxCode: String,
        message: String?
    ): Result<LinkRequest> = runCatching {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.PATIENT) {
            throw IllegalStateException("Funzione disponibile solo per i pazienti")
        }
        val trimmedMessage = message?.trim()?.takeIf { it.isNotBlank() }
        require((trimmedMessage?.length ?: 0) <= MAX_MESSAGE_LENGTH) {
            "Il messaggio non può superare $MAX_MESSAGE_LENGTH caratteri"
        }
        linkRequestRepository.sendRequest(
            patientTaxCode = authUser.taxCode,
            specialistTaxCode = specialistTaxCode,
            message = trimmedMessage
        ).getOrThrow()
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 500
    }
}