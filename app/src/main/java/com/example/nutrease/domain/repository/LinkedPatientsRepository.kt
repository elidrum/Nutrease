package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.LinkedPatient

/** Contratto del dominio per la lista pazienti dello specialista (RF18). */
interface LinkedPatientsRepository {
    suspend fun getLinkedPatients(specialistTaxCode: String): Result<List<LinkedPatient>>
}