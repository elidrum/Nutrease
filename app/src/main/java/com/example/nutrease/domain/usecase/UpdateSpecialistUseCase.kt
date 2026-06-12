package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.repository.UserRepository
import javax.inject.Inject

/** Salvataggio delle modifiche all'anagrafica dello specialista (RF5). */
class UpdateSpecialistUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(specialist: Specialist): Result<Unit> =
        userRepository.updateSpecialist(specialist)
}