package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.UserProfile
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Profilo completo dell'utente loggato (RF5): combina la sessione ([AuthRepository])
 * con l'anagrafica letta dalla tabella giusta in base al ruolo. La segretaria ricade
 * sul ramo specialista perché non ha un'anagrafica dedicata nell'MVP.
 */
class GetProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): Result<UserProfile> = runCatching {
        val authUser = authRepository.getCurrentUser()
            ?: throw Exception("Nessun utente autenticato")

        when (authUser.role) {
            UserRole.PATIENT -> {
                val patient = userRepository.getPatient(authUser.taxCode).getOrThrow()
                UserProfile.PatientProfile(authUser, patient)
            }
            UserRole.SPECIALIST, UserRole.SECRETARY -> {
                val specialist = userRepository.getSpecialist(authUser.taxCode).getOrThrow()
                UserProfile.SpecialistProfile(authUser, specialist)
            }
        }
    }
}