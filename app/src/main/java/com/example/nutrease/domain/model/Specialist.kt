package com.example.nutrease.domain.model

/**
 * Profilo dello specialista (tabella `specialista`). Come per [Patient], il codice
 * fiscale è la chiave di riferimento e [authUid] collega l'account Supabase;
 * [specialization] e [city] alimentano i filtri della discovery lato paziente.
 */
data class Specialist(
    val taxCode: String,
    val authUid: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val vatNumber: String,
    val info: String?,
    val specialization: SpecializationType?,
    val city: String?,
    // Approvazione admin: false = registrato ma non ancora abilitato.
    // Default true: gli specialisti visti in discovery / collegati sono sempre approvati;
    // l'unico caso false è il proprio profilo appena registrato, letto via SpecialistDto.
    val isVerified: Boolean = true
)