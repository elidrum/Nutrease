package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.SpecializationType
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.SpecialistDirectoryRepository
import javax.inject.Inject

/**
 * Discovery specialisti per il paziente loggato (RF13): risolve il codice fiscale da
 * escludere (già collegati / richieste pendenti) e delega la ricerca paginata al
 * repository. Solo i pazienti possono cercare (controllo di ruolo qui, non in UI).
 */
class SearchSpecialistsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val specialistDirectoryRepository: SpecialistDirectoryRepository
) {
    suspend operator fun invoke(
        query: String,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        specialization: SpecializationType? = null,
        city: String? = null
    ): Result<List<Specialist>> = runCatching {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.PATIENT) {
            throw IllegalStateException("Funzione disponibile solo per i pazienti")
        }
        specialistDirectoryRepository.searchSpecialists(
            query = query.trim(),
            excludeForPatientTaxCode = authUser.taxCode,
            page = page,
            pageSize = pageSize,
            specialization = specialization,
            city = city?.trim()?.takeIf { it.isNotBlank() }
        ).getOrThrow()
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}