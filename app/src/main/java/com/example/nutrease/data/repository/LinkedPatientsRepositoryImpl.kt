package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.LinkedPatientDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.domain.model.LinkedPatient
import com.example.nutrease.domain.repository.LinkedPatientsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementazione di [LinkedPatientsRepository]: select su `fascicoloclinico` (stato
 * Attivo) con embed `paziente(...)` per avere fascicolo e anagrafica in una query.
 * Ordinamento per cognome client-side: la lista è piccola e PostgREST non ordina
 * direttamente sulle colonne della tabella embedded.
 */
class LinkedPatientsRepositoryImpl(
    private val supabase: SupabaseClient
) : LinkedPatientsRepository {

    override suspend fun getLinkedPatients(
        specialistTaxCode: String
    ): Result<List<LinkedPatient>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("fascicoloclinico")
                .select(
                    Columns.raw(
                        """"IdFascicolo","CodFiscalePaziente",
                           paziente("CodiceFiscale","Nome","Cognome","Email","DataNascita")"""
                            .trimIndent()
                    )
                ) {
                    filter {
                        eq("CodFiscaleSpecialista", specialistTaxCode)
                        eq("Stato", "Attivo")
                    }
                }
                .decodeList<LinkedPatientDto>()
                .map { it.toDomain() }
                .sortedWith(compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }))
        }
    }
}