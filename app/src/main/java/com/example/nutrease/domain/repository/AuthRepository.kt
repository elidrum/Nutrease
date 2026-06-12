package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.RegisterData

/**
 * Contratto del dominio per autenticazione e ciclo di vita dell'account (RF1–RF7).
 * L'implementazione (`AuthRepositoryImpl`, layer data) parla con Supabase Auth e mappa
 * le eccezioni su `DomainError`; gli UseCase dipendono solo da questa interfaccia.
 */
interface AuthRepository {

    /** Accesso email+password (RF3). In caso di credenziali errate fallisce con `DomainError.InvalidCredentials`. */
    suspend fun login(email: String, password: String): Result<AuthUser>

    /**
     * Registra un nuovo utente. La creazione del profilo (profilo_utente +
     * paziente/specialista) avviene server-side in modo atomico tramite il
     * trigger `crea_profilo_da_auth` su `auth.users` (vedi sezione 6.9 di
     * `sql/nutreaseDatabase.sql`). Se una qualunque delle insert lato DB
     * fallisce, anche l'utente in `auth.users` viene rollbackato.
     */
    suspend fun register(data: RegisterData): Result<Unit>

    /** Utente della sessione persistita, o `null` se non c'è (usato dall'auto-login allo splash). */
    suspend fun getCurrentUser(): AuthUser?

    /** Chiude la sessione corrente (RF4). */
    suspend fun logout()

    /**
     * Ri-verifica le credenziali dell'utente corrente (re-auth) prima di
     * un'operazione sensibile (cambio password RF6, eliminazione account RF7).
     * Fallisce se la password è errata.
     */
    suspend fun reauthenticate(email: String, password: String): Result<Unit>

    /** Invia l'email di reimpostazione password all'indirizzo indicato (RF). */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Completa il reset password con il codice OTP a 6 cifre ricevuto via email: verifica il
     * codice (recovery OTP) per stabilire una sessione di recupero e imposta [newPassword].
     * Evita i magic-link/Site URL (niente deep-link, niente localhost).
     */
    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): Result<Unit>

    /**
     * Elimina definitivamente l'account dell'utente corrente (RF7), lato server,
     * tramite la RPC `delete_own_account` (SECURITY DEFINER): rimuove i dati di
     * dominio (fascicolo, diari, chat, richieste…) e la riga in `auth.users`, così
     * l'account non resta in limbo. Va chiamata dopo la re-auth.
     *
     * Fallisce con [com.example.nutrease.domain.model.DomainError.StillHasLinkedPatients]
     * se uno specialista ha ancora pazienti collegati (non si cancellano i loro dati).
     */
    suspend fun deleteAccount(): Result<Unit>
}