package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.LinkRequestWithPatient
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Proiezione a una colonna di `richiesta_collegamento`, per le esclusioni in discovery. */
@Serializable
data class LinkRequestSpecialistRefDto(
    @SerialName("CodFiscaleSpecialista") val specialistTaxCode: String
)

/** Proiezione a una colonna di `fascicoloclinico`, per le esclusioni in discovery. */
@Serializable
data class FascicoloSpecialistRefDto(
    @SerialName("CodFiscaleSpecialista") val specialistTaxCode: String
)

/** Riga completa di `richiesta_collegamento`; i default coprono i campi generati dal DB. */
@Serializable
data class LinkRequestDto(
    @SerialName("IdRichiesta") val id: Long? = null,
    @SerialName("CodFiscalePaziente") val patientTaxCode: String,
    @SerialName("CodFiscaleSpecialista") val specialistTaxCode: String,
    @SerialName("Stato") val status: String = "In Attesa",
    @SerialName("MessaggioRichiesta") val message: String? = null,
    @SerialName("DataRichiesta") val requestedAt: String? = null,
    @SerialName("DataRisposta") val respondedAt: String? = null,
    @SerialName("MotivazioneRifiuto") val rejectionReason: String? = null
)

@Serializable
data class PatientShortDto(
    @SerialName("Nome") val firstName: String,
    @SerialName("Cognome") val lastName: String
)

/**
 * Richiesta + nome del paziente in un'unica query: il campo [patient] è popolato
 * dall'embed PostgREST `paziente(Nome,Cognome)`, che segue la foreign key e annida
 * l'oggetto nel JSON di risposta (niente seconda query né join manuale).
 */
@Serializable
data class LinkRequestWithPatientDto(
    @SerialName("IdRichiesta") val id: Long,
    @SerialName("CodFiscalePaziente") val patientTaxCode: String,
    @SerialName("CodFiscaleSpecialista") val specialistTaxCode: String,
    @SerialName("Stato") val status: String,
    @SerialName("MessaggioRichiesta") val message: String? = null,
    @SerialName("DataRichiesta") val requestedAt: String,
    @SerialName("DataRisposta") val respondedAt: String? = null,
    @SerialName("MotivazioneRifiuto") val rejectionReason: String? = null,
    @SerialName("paziente") val patient: PatientShortDto
)

fun LinkRequestDto.toDomain(): LinkRequest = LinkRequest(
    id = id ?: 0L,
    patientTaxCode = patientTaxCode,
    specialistTaxCode = specialistTaxCode,
    status = LinkRequestStatus.fromDbLabel(status),
    message = message,
    requestedAt = requestedAt?.let { Instant.parse(it) }
        ?: Instant.fromEpochMilliseconds(0),
    respondedAt = respondedAt?.let { Instant.parse(it) },
    rejectionReason = rejectionReason
)

fun LinkRequestWithPatientDto.toDomain(): LinkRequestWithPatient = LinkRequestWithPatient(
    request = LinkRequest(
        id = id,
        patientTaxCode = patientTaxCode,
        specialistTaxCode = specialistTaxCode,
        status = LinkRequestStatus.fromDbLabel(status),
        message = message,
        requestedAt = Instant.parse(requestedAt),
        respondedAt = respondedAt?.let { Instant.parse(it) },
        rejectionReason = rejectionReason
    ),
    patientFirstName = patient.firstName,
    patientLastName = patient.lastName
)