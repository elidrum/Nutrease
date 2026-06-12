package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.LinkedPatient
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Anagrafica essenziale del paziente annidata nell'embed di [LinkedPatientDto]. */
@Serializable
data class LinkedPatientProfileDto(
    @SerialName("CodiceFiscale") val taxCode: String,
    @SerialName("Nome") val firstName: String,
    @SerialName("Cognome") val lastName: String,
    @SerialName("Email") val email: String? = null,
    @SerialName("DataNascita") val birthDate: String? = null
)

/** Riga di `fascicoloclinico` + paziente embed (una query per la lista RF18). */
@Serializable
data class LinkedPatientDto(
    @SerialName("IdFascicolo") val fascicoloId: Int,
    @SerialName("CodFiscalePaziente") val patientTaxCode: String,
    @SerialName("paziente") val patient: LinkedPatientProfileDto
)

fun LinkedPatientDto.toDomain(): LinkedPatient = LinkedPatient(
    taxCode = patient.taxCode,
    fascicoloId = fascicoloId,
    firstName = patient.firstName,
    lastName = patient.lastName,
    email = patient.email,
    birthDate = patient.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
)