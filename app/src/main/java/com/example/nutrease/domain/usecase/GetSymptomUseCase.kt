package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.repository.SymptomRepository
import javax.inject.Inject

/** Lettura di un singolo sintomo, usata per precompilare il form in modifica (RF12). */
class GetSymptomUseCase @Inject constructor(
    private val symptomRepository: SymptomRepository
) {
    suspend operator fun invoke(symptomId: Long): Result<Symptom> =
        symptomRepository.getSymptom(symptomId)
}
