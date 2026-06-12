package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.repository.SymptomRepository
import javax.inject.Inject

/** Modifica di un sintomo esistente (RF12); stesse invarianti dell'inserimento. */
class UpdateSymptomUseCase @Inject constructor(
    private val symptomRepository: SymptomRepository
) {
    suspend operator fun invoke(symptom: Symptom): Result<Unit> {
        if (symptom.id == null) {
            return Result.failure(IllegalArgumentException("Sintomo senza ID: impossibile aggiornare"))
        }
        if (symptom.severity == SymptomSeverity.NONE) {
            return Result.failure(IllegalArgumentException("Seleziona un livello di severità"))
        }
        return symptomRepository.updateSymptom(symptom)
    }
}
