package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.SpecialistDirectoryRepository
import javax.inject.Inject

/**
 * Specialista attualmente collegato al paziente loggato (fascicolo clinico attivo),
 * null se il paziente non è collegato a nessuno. Alimenta la card "Il tuo specialista"
 * in home e l'avviso di sostituzione in discovery: l'accettazione di una nuova
 * richiesta sostituisce il collegamento attivo (UNIQUE sul fascicolo per paziente).
 */
class GetLinkedSpecialistUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val specialistDirectoryRepository: SpecialistDirectoryRepository
) {
    suspend operator fun invoke(): Result<Specialist?> = runCatching {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.PATIENT) {
            throw IllegalStateException("Funzione disponibile solo per i pazienti")
        }
        specialistDirectoryRepository.getLinkedSpecialist(authUser.taxCode).getOrThrow()
    }
}
