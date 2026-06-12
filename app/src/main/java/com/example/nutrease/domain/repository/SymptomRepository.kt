package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.Symptom
import kotlinx.datetime.LocalDate

/**
 * Contratto del dominio per i sintomi del diario (RF10, RF12). Come [DiaryRepository],
 * le letture sono parametriche sul fascicolo e riusate anche dallo specialista (RLS).
 */
interface SymptomRepository {

    suspend fun getSymptomsForDate(fascicoloId: Int, date: LocalDate): Result<List<Symptom>>

    suspend fun getSymptom(symptomId: Long): Result<Symptom>

    suspend fun addSymptom(symptom: Symptom): Result<Long>

    suspend fun updateSymptom(symptom: Symptom): Result<Unit>

    suspend fun deleteSymptom(symptomId: Long): Result<Unit>
}
