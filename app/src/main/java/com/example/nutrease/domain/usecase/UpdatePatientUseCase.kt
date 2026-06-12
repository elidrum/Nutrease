package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Patient
import com.example.nutrease.domain.repository.UserRepository
import javax.inject.Inject

/** Salvataggio delle modifiche all'anagrafica del paziente (RF5). */
class UpdatePatientUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(patient: Patient): Result<Unit> =
        userRepository.updatePatient(patient)
}