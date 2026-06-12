package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.UserRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella `profilo_utente`: collega l'account Supabase (`auth_uid`) a ruolo
 * e codice fiscale. Come tutti i DTO, replica i nomi colonna del DB via [SerialName]
 * e viene tradotto in tipi di dominio dai mapper in questo package.
 */
@Serializable
data class UserProfileDto(
    @SerialName("auth_uid") val authUid: String,
    @SerialName("ruolo") val ruolo: String,
    @SerialName("codice_fiscale") val codiceFiscale: String
)

/** Traduce l'etichetta italiana del DB nell'enum di dominio; ruolo ignoto = dato corrotto, meglio fallire subito. */
fun UserProfileDto.toUserRole(): UserRole = when (ruolo) {
    "paziente" -> UserRole.PATIENT
    "specialista" -> UserRole.SPECIALIST
    "segretaria" -> UserRole.SECRETARY
    else -> throw IllegalArgumentException("Ruolo sconosciuto: $ruolo")
}