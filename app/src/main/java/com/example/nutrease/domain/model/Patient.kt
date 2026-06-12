package com.example.nutrease.domain.model

import kotlinx.datetime.LocalDate

/**
 * Profilo anagrafico del paziente (tabella `paziente`). Il codice fiscale è la chiave
 * con cui le altre tabelle (fascicolo, richieste, chat) lo referenziano; [authUid] lo
 * collega all'account Supabase. I campi nullable sono facoltativi alla registrazione.
 */
data class Patient(
    val taxCode: String,
    val authUid: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val gender: Gender,
    val birthDate: LocalDate,
    val street: String?,
    val city: String?,
    val postalCode: String?
)