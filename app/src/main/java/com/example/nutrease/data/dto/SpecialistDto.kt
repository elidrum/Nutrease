package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.SpecializationType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella `specialista`; tradotta in [Specialist] dal mapper sotto.
 * I nullable con default coprono le query che selezionano solo alcune colonne.
 */
@Serializable
data class SpecialistDto(
    @SerialName("CodiceFiscale") val taxCode: String,
    @SerialName("auth_uid") val authUid: String? = null,
    @SerialName("Nome") val firstName: String,
    @SerialName("Cognome") val lastName: String,
    @SerialName("Telefono") val phone: String? = null,
    @SerialName("Email") val email: String? = null,
    @SerialName("PartitaIVA") val vatNumber: String,
    @SerialName("Info") val info: String? = null,
    @SerialName("Specializzazione") val specialization: String? = null,
    @SerialName("Citta") val city: String? = null,
    // Default false: assente nelle query di discovery (che selezionano colonne specifiche),
    // valorizzato quando si legge il profilo completo dello specialista.
    @SerialName("Verificato") val isVerified: Boolean = false
)

fun SpecialistDto.toDomain(): Specialist = Specialist(
    taxCode = taxCode,
    authUid = authUid ?: "",
    firstName = firstName,
    lastName = lastName,
    email = email ?: "",
    phone = phone,
    vatNumber = vatNumber,
    info = info,
    specialization = SpecializationType.fromDbLabel(specialization),
    city = city,
    isVerified = isVerified
)
