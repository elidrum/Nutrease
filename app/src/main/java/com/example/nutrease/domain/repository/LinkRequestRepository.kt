package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.LinkRequestWithPatient

/** Contratto del dominio per le richieste di collegamento (RF14–RF17). */
interface LinkRequestRepository {

    /**
     * Invia (o re-invia) la richiesta del paziente allo specialista. È un upsert:
     * se esiste già una richiesta rifiutata verso lo stesso specialista, la riporta
     * a "In Attesa" invece di violare il vincolo UNIQUE sulla coppia di codici fiscali.
     */
    suspend fun sendRequest(
        patientTaxCode: String,
        specialistTaxCode: String,
        message: String?
    ): Result<LinkRequest>

    suspend fun getReceivedRequests(
        specialistTaxCode: String,
        status: LinkRequestStatus = LinkRequestStatus.PENDING
    ): Result<List<LinkRequestWithPatient>>

    suspend fun getSentRequests(patientTaxCode: String): Result<List<LinkRequest>>

    /**
     * Accetta la richiesta (RF16): il client aggiorna solo stato e data risposta;
     * fascicolo clinico e chat li crea un trigger sul DB nella stessa transazione.
     */
    suspend fun acceptRequest(requestId: Long): Result<Unit>

    /** Rifiuta la richiesta con motivazione (RF17, obbligatoria: la impone lo UseCase). */
    suspend fun rejectRequest(requestId: Long, reason: String): Result<Unit>
}