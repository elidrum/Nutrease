package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.FascicoloSpecialistRefDto
import com.example.nutrease.data.dto.FascicoloWithSpecialistDto
import com.example.nutrease.data.dto.LinkRequestSpecialistRefDto
import com.example.nutrease.data.dto.SpecialistDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.SpecializationType
import com.example.nutrease.domain.repository.SpecialistDirectoryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementazione di [SpecialistDirectoryRepository]. I filtri testo/specializzazione/
 * città girano server-side (`ILIKE`/`eq`, colonne indicizzate); l'esclusione dei già
 * collegati o con richiesta pendente avviene client-side: per compensare, la pagina
 * fetcha `pageSize + |esclusi|` righe, così dopo il filtro la pagina resta piena.
 * Nota RLS: la policy di select mostra ai pazienti solo specialisti verificati.
 */
class SpecialistDirectoryRepositoryImpl(
    private val supabase: SupabaseClient
) : SpecialistDirectoryRepository {

    override suspend fun searchSpecialists(
        query: String,
        excludeForPatientTaxCode: String,
        page: Int,
        pageSize: Int,
        specialization: SpecializationType?,
        city: String?
    ): Result<List<Specialist>> = withContext(Dispatchers.IO) {
        runCatching {
            val excluded = collectExcludedTaxCodes(excludeForPatientTaxCode)
            val fetchSize = pageSize + excluded.size
            val from = (page * pageSize).toLong()
            val to = from + fetchSize - 1

            val results = supabase.from("specialista")
                .select {
                    filter {
                        if (query.isNotBlank()) {
                            val pattern = "%$query%"
                            or {
                                ilike("Nome", pattern)
                                ilike("Cognome", pattern)
                                ilike("Info", pattern)
                            }
                        }
                        specialization?.let { eq("Specializzazione", it.dbLabel) }
                        city?.takeIf { it.isNotBlank() }?.let {
                            ilike("Citta", "%$it%")
                        }
                    }
                    order("Cognome", Order.ASCENDING)
                    order("Nome", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<SpecialistDto>()

            results
                .filter { it.taxCode !in excluded }
                .take(pageSize)
                .map { it.toDomain() }
        }
    }

    override suspend fun getLinkedSpecialist(
        patientTaxCode: String
    ): Result<Specialist?> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("fascicoloclinico")
                .select(
                    Columns.raw(
                        """"IdFascicolo",
                           specialista("CodiceFiscale","Nome","Cognome","PartitaIVA",
                           "Specializzazione","Citta","Verificato")"""
                            .trimIndent()
                    )
                ) {
                    filter {
                        eq("CodFiscalePaziente", patientTaxCode)
                        eq("Stato", "Attivo")
                    }
                    limit(1)
                }
                .decodeList<FascicoloWithSpecialistDto>()
                .firstOrNull()
                ?.specialist
                ?.toDomain()
        }
    }

    /** Codici fiscali da nascondere in discovery: specialisti già collegati (fascicolo attivo) o con richiesta pendente. */
    private suspend fun collectExcludedTaxCodes(patientTaxCode: String): Set<String> {
        val linked = supabase.from("fascicoloclinico")
            .select {
                filter {
                    eq("CodFiscalePaziente", patientTaxCode)
                    eq("Stato", "Attivo")
                }
            }
            .decodeList<FascicoloSpecialistRefDto>()
            .map { it.specialistTaxCode }

        val pending = supabase.from("richiesta_collegamento")
            .select {
                filter {
                    eq("CodFiscalePaziente", patientTaxCode)
                    eq("Stato", "In Attesa")
                }
            }
            .decodeList<LinkRequestSpecialistRefDto>()
            .map { it.specialistTaxCode }

        return (linked + pending).toSet()
    }
}