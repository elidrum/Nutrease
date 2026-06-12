package com.example.nutrease.domain.model

/**
 * Profilo completo dell'utente loggato: sessione ([AuthUser]) + anagrafica per ruolo.
 * Sealed class invece di due campi nullable: chi consuma il profilo (es. ProfileScreen)
 * fa un `when` esaustivo e il compilatore garantisce che entrambi i casi siano gestiti.
 */
sealed class UserProfile {
    data class PatientProfile(
        val authUser: AuthUser,
        val patient: Patient
    ) : UserProfile()

    data class SpecialistProfile(
        val authUser: AuthUser,
        val specialist: Specialist
    ) : UserProfile()
}