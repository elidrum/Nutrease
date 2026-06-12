package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.SpecializationType

/** Contratto del dominio per la discovery degli specialisti lato paziente (RF13). */
interface SpecialistDirectoryRepository {

    /**
     * Pagina di specialisti che corrispondono ai filtri (testo, specializzazione, città),
     * esclusi quelli già collegati al paziente o con richiesta pendente
     * ([excludeForPatientTaxCode]). [page] parte da 0; filtri null = nessun filtro.
     */
    suspend fun searchSpecialists(
        query: String,
        excludeForPatientTaxCode: String,
        page: Int,
        pageSize: Int,
        specialization: SpecializationType? = null,
        city: String? = null
    ): Result<List<Specialist>>

    /**
     * Specialista titolare del fascicolo clinico attivo del paziente, null se il paziente
     * non è collegato. Il vincolo UNIQUE("CodFiscalePaziente") sul fascicolo garantisce
     * al più un collegamento attivo per paziente.
     */
    suspend fun getLinkedSpecialist(patientTaxCode: String): Result<Specialist?>
}