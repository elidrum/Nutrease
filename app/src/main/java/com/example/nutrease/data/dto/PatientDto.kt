package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.Gender
import com.example.nutrease.domain.model.Patient
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella `paziente`. I nullable con default coprono le query che non
 * selezionano tutte le colonne (es. embed con poche colonne); i mapper sotto traducono
 * verso/da [Patient] convertendo genere e data (il DB usa stringhe, il dominio tipi veri).
 */
@Serializable
data class PatientDto(
    @SerialName("CodiceFiscale") val taxCode: String,
    @SerialName("auth_uid") val authUid: String? = null,
    @SerialName("Nome") val firstName: String,
    @SerialName("Cognome") val lastName: String,
    @SerialName("Telefono") val phone: String? = null,
    @SerialName("Email") val email: String? = null,
    @SerialName("Sesso") val gender: String,
    @SerialName("DataNascita") val birthDate: String,
    @SerialName("Via") val street: String? = null,
    @SerialName("Citta") val city: String? = null,
    @SerialName("CAP") val postalCode: String? = null
)

fun PatientDto.toDomain(): Patient = Patient(
    taxCode = taxCode,
    authUid = authUid ?: "",
    firstName = firstName,
    lastName = lastName,
    email = email ?: "",
    phone = phone,
    gender = gender.toGender(),
    birthDate = LocalDate.parse(birthDate),
    street = street,
    city = city,
    postalCode = postalCode
)

fun Patient.toDto(): PatientDto = PatientDto(
    taxCode = taxCode,
    authUid = authUid,
    firstName = firstName,
    lastName = lastName,
    phone = phone,
    email = email,
    gender = gender.toDbValue(),
    birthDate = birthDate.toString(),
    street = street,
    city = city,
    postalCode = postalCode
)

private fun String.toGender(): Gender = when (this) {
    "M" -> Gender.M
    "F" -> Gender.F
    else -> Gender.OTHER
}

private fun Gender.toDbValue(): String = when (this) {
    Gender.M -> "M"
    Gender.F -> "F"
    Gender.OTHER -> "Altro"
}