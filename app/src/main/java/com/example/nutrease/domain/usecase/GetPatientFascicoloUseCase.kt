package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.DiaryRepository
import javax.inject.Inject

/**
 * Risolve il fascicolo clinico attivo del paziente loggato: è il prerequisito del
 * diario (pasti e sintomi appartengono al fascicolo, non direttamente al paziente).
 * Controlla anche il ruolo: la funzione non ha senso per lo specialista.
 */
class GetPatientFascicoloUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val diaryRepository: DiaryRepository
) {
    suspend operator fun invoke(): Result<Int> = runCatching {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.PATIENT) {
            throw IllegalStateException("Funzione disponibile solo per i pazienti")
        }
        diaryRepository.getFascicoloIdForPatient(authUser.taxCode).getOrThrow()
    }
}