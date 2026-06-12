package com.example.nutrease.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Proiezione minima di `fascicoloclinico`: serve solo l'id del fascicolo attivo del paziente. */
@Serializable
data class FascicoloIdDto(
    @SerialName("IdFascicolo") val id: Int
)

/**
 * Fascicolo attivo del paziente con embed dello specialista titolare (card "Il tuo specialista").
 * [specialist] è nullable: se la RLS filtra la riga embedded (specialista de-verificato dopo
 * il collegamento) PostgREST manda `"specialista": null` — va trattato come "nessuno specialista
 * visibile", non come errore di decodifica.
 */
@Serializable
data class FascicoloWithSpecialistDto(
    @SerialName("IdFascicolo") val id: Int,
    @SerialName("specialista") val specialist: SpecialistDto? = null
)