package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.repository.SymptomRepository
import javax.inject.Inject

/**
 * Registrazione di un sintomo (RF10). NONE esiste come livello di lettura (bucket dei
 * valori bassi 1–2) ma non è un dato sensato da inserire: si blocca prima della rete.
 */
class AddSymptomUseCase @Inject constructor(
    private val symptomRepository: SymptomRepository
) {
    suspend operator fun invoke(symptom: Symptom): Result<Long> {
        if (symptom.severity == SymptomSeverity.NONE) {
            return Result.failure(IllegalArgumentException("Seleziona un livello di severità"))
        }
        return symptomRepository.addSymptom(symptom)
    }
}
