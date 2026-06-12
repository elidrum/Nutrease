package com.example.nutrease.domain.model

/**
 * Errori di dominio con messaggi utente già generici e privi di dettagli tecnici.
 *
 * Il layer `data` mappa le eccezioni di Supabase/rete su questi tipi; la UI mostra
 * direttamente [message]. Obiettivi: niente user-enumeration (login e re-auth danno
 * lo stesso esito per email inesistente e password errata) e nessun dettaglio
 * tecnico (URL, SQL, stacktrace) esposto in interfaccia.
 *
 * Dominio puro: estende `kotlin.Exception` (nessuna dipendenza Android), così può
 * viaggiare dentro `Result.failure(...)` come gli altri errori del progetto.
 */
sealed class DomainError(message: String) : Exception(message) {

    /** Login/re-auth: email inesistente o password errata. Messaggio unico: niente enumeration. */
    data object InvalidCredentials : DomainError("Credenziali non valide")

    /** Email malformata in input (es. reset password). */
    data object InvalidEmail : DomainError("Inserisci un'email valida")

    /** Cambio password: la nuova coincide con quella attuale (GoTrue `same_password`). */
    data object SamePassword : DomainError("La nuova password deve essere diversa dalla precedente")

    /** Reset password: codice OTP errato o scaduto. */
    data object InvalidResetCode : DomainError("Codice non valido o scaduto. Richiedine uno nuovo.")

    /** Problema di rete/connessione. */
    data object Network : DomainError("Connessione assente o instabile. Riprova.")

    /** Sessione assente o scaduta: serve un nuovo accesso. */
    data object NotAuthenticated : DomainError("Sessione scaduta. Effettua di nuovo l'accesso.")

    /** Auth riuscita ma profilo mancante (account in limbo da registrazione incompleta). */
    data object ProfileNotFound : DomainError("Profilo non trovato. Completa la registrazione.")

    /** Eliminazione account specialista bloccata: ha ancora pazienti collegati. */
    data object StillHasLinkedPatients :
        DomainError("Hai ancora pazienti collegati. Scollegali prima di eliminare l'account.")

    /** Fallback per errori non previsti: nessun dettaglio tecnico in UI. */
    data object Unknown : DomainError("Si è verificato un errore. Riprova.")
}
