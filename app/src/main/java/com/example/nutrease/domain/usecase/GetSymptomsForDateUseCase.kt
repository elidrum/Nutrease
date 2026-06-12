package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.repository.SymptomRepository
import kotlinx.datetime.LocalDate
import javax.inject.Inject

/** Sintomi di una data per un fascicolo (RF11); riusato anche dalla vista specialista. */
class GetSymptomsForDateUseCase @Inject constructor(
    private val symptomRepository: SymptomRepository
) {
    suspend operator fun invoke(fascicoloId: Int, date: LocalDate): Result<List<Symptom>> =
        symptomRepository.getSymptomsForDate(fascicoloId, date)
}