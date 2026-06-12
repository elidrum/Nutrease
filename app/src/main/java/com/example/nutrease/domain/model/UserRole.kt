package com.example.nutrease.domain.model

/**
 * Ruolo dell'utente, deciso alla registrazione e salvato in `profilo_utente`.
 * Determina la home mostrata dopo il login e le operazioni consentite
 * (es. solo il paziente scrive sul diario, solo lo specialista accetta richieste).
 * SECRETARY è previsto dallo schema ma non ha UI nell'MVP.
 */
enum class UserRole {
    PATIENT,
    SPECIALIST,
    SECRETARY
}