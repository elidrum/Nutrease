package com.example.nutrease.domain.model

import kotlinx.datetime.LocalDate

/**
 * Paziente collegato visto dallo specialista (RF18): anagrafica essenziale + id del
 * fascicolo attivo, che è la chiave con cui si apre il suo diario (RF19).
 */
data class LinkedPatient(
    val taxCode: String,
    val fascicoloId: Int,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val birthDate: LocalDate?
)