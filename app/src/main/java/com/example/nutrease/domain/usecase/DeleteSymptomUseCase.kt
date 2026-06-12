package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.SymptomRepository
import javax.inject.Inject

/** Eliminazione di un sintomo dal diario (RF12). */
class DeleteSymptomUseCase @Inject constructor(
    private val symptomRepository: SymptomRepository
) {
    suspend operator fun invoke(symptomId: Long): Result<Unit> =
        symptomRepository.deleteSymptom(symptomId)
}