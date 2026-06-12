package com.example.nutrease.domain.model

import kotlin.time.Instant

/**
 * Richiesta di collegamento paziente → specialista (tabella `richiesta_collegamento`,
 * RF14–RF17). I campi risposta ([respondedAt], [rejectionReason]) sono nulli finché
 * lo specialista non accetta/rifiuta; un CHECK sul DB ne impone la coerenza con lo stato.
 */
data class LinkRequest(
    val id: Long,
    val patientTaxCode: String,
    val specialistTaxCode: String,
    val status: LinkRequestStatus,
    val message: String?,
    val requestedAt: Instant,
    val respondedAt: Instant?,
    val rejectionReason: String?
)

/** Richiesta + nome del paziente (dal join), per la inbox dello specialista (RF15). */
data class LinkRequestWithPatient(
    val request: LinkRequest,
    val patientFirstName: String,
    val patientLastName: String
)