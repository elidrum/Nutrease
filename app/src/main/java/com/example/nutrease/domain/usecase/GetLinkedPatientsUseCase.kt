package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.LinkedPatient
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.LinkedPatientsRepository
import javax.inject.Inject

/** Pazienti con fascicolo attivo collegati allo specialista loggato (RF18). */
class GetLinkedPatientsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val linkedPatientsRepository: LinkedPatientsRepository
) {
    suspend operator fun invoke(): Result<List<LinkedPatient>> = runCatching {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.SPECIALIST) {
            throw IllegalStateException("Funzione disponibile solo per gli specialisti")
        }
        linkedPatientsRepository.getLinkedPatients(authUser.taxCode).getOrThrow()
    }
}