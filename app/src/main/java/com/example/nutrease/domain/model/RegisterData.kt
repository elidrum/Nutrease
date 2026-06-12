package com.example.nutrease.domain.model

import kotlinx.datetime.LocalDate

/**
 * Dati del form di registrazione. Sealed class con due varianti (paziente/specialista):
 * i campi comuni stanno nella base astratta, quelli specifici nelle sottoclassi, così
 * `RegisterUseCase` accetta un solo tipo e distingue il ramo con un `when` esaustivo.
 * Questi dati viaggiano come metadata del signUp Supabase: è un trigger sul DB a creare
 * profilo e riga paziente/specialista nella stessa transazione dell'account.
 */
sealed class RegisterData {
    abstract val email: String
    abstract val password: String
    abstract val firstName: String
    abstract val lastName: String
    abstract val taxCode: String

    data class PatientData(
        override val email: String,
        override val password: String,
        override val firstName: String,
        override val lastName: String,
        override val taxCode: String,
        val gender: Gender,
        val birthDate: LocalDate
    ) : RegisterData()

    data class SpecialistData(
        override val email: String,
        override val password: String,
        override val firstName: String,
        override val lastName: String,
        override val taxCode: String,
        val vatNumber: String,
        val specialization: SpecializationType,
        val city: String
    ) : RegisterData()
}