package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.UserProfileDto
import com.example.nutrease.data.dto.toUserRole
import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.model.Gender
import com.example.nutrease.domain.model.RegisterData
import com.example.nutrease.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Implementazione di [AuthRepository] su Supabase Auth (GoTrue) + tabella `profilo_utente`.
 * Ogni metodo gira su [Dispatchers.IO] (parsing JSON e costruzione oggetti fuori dal main
 * thread) e converte le eccezioni del client in [DomainError] prima di risalire ai ViewModel.
 */
class AuthRepositoryImpl(
    private val supabase: SupabaseClient
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<AuthUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                try {
                    supabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                } catch (_: RestException) {
                    // GoTrue rifiuta email inesistente e password errata con lo stesso
                    // errore: lo normalizziamo a un messaggio unico e generico, così la UI
                    // non distingue i due casi (niente user-enumeration né dettagli tecnici).
                    throw DomainError.InvalidCredentials
                }
                val user = supabase.auth.currentSessionOrNull()?.user
                    ?: throw DomainError.NotAuthenticated

                val profile = supabase.from("profilo_utente")
                    .select { filter { eq("auth_uid", user.id) } }
                    .decodeList<UserProfileDto>()
                    .firstOrNull()
                    ?: throw DomainError.ProfileNotFound

                AuthUser(
                    id = user.id,
                    email = user.email ?: "",
                    role = profile.toUserRole(),
                    taxCode = profile.codiceFiscale
                )
            }.mapToDomainError()
        }

    override suspend fun getCurrentUser(): AuthUser? = withContext(Dispatchers.IO) {
        runCatching {
            // Attende il caricamento della sessione persistita da storage, così
            // l'auto-login all'avvio non vede una sessione "non ancora pronta".
            supabase.auth.awaitInitialization()
            val user = supabase.auth.currentSessionOrNull()?.user ?: return@runCatching null
            val profile = supabase.from("profilo_utente")
                .select { filter { eq("auth_uid", user.id) } }
                .decodeList<UserProfileDto>()
                .firstOrNull() ?: return@runCatching null
            AuthUser(
                id = user.id,
                email = user.email ?: "",
                role = profile.toUserRole(),
                taxCode = profile.codiceFiscale
            )
        }.getOrNull()
    }

    override suspend fun register(data: RegisterData): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.auth.signUpWith(Email) {
                    this.email = data.email
                    this.password = data.password
                    this.data = data.toAuthMetadata()
                }
                Unit
            }
        }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        supabase.auth.signOut()
    }

    override suspend fun reauthenticate(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Ri-effettua il sign-in con le credenziali correnti: se la password è
                // errata, signInWith solleva una RestException → DomainError.InvalidCredentials.
                try {
                    supabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                } catch (_: RestException) {
                    throw DomainError.InvalidCredentials
                }
            }.mapToDomainError()
        }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            // resetPasswordForEmail risponde 200 a prescindere dall'esistenza dell'email
            // (anti-enumeration lato GoTrue): il chiamante riceve sempre success salvo
            // problemi di rete. Vedi nota deep-link/redirect in docs/architecture.md.
            runCatching { supabase.auth.resetPasswordForEmail(email) }.mapToDomainError()
        }

    override suspend fun confirmPasswordReset(
        email: String,
        code: String,
        newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Recovery via OTP a 6 cifre (NIENTE magic-link/Site URL → niente localhost). Richiede
            // che il template email "Reset Password" su Supabase usi {{ .Token }} (vedi docs/setup.md).
            // verifyEmailOtp stabilisce una sessione di recupero; updateUser imposta la nuova password.
            supabase.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = email.trim(),
                token = code.trim()
            )
            supabase.auth.updateUser { password = newPassword }
            // La verifica OTP apre una sessione di recovery: la chiudiamo (best-effort) così il
            // flusso torna pulito al login. Best-effort = il reset è già riuscito, non va fallito
            // se il logout ha un intoppo di rete.
            runCatching { supabase.auth.signOut() }
            Unit
        }.recoverCatching { e ->
            // Nel flusso di recovery l'unico errore REST atteso riguarda il codice (errato/scaduto).
            throw when (e) {
                is RestException -> DomainError.InvalidResetCode
                is HttpRequestException -> DomainError.Network
                else -> DomainError.Unknown
            }
        }
    }

    override suspend fun deleteAccount(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Eliminazione server-side: la funzione SECURITY DEFINER rimuove i dati di
                // dominio + la riga in auth.users (il client non ha né le policy DELETE su
                // paziente/specialista né i permessi su auth.users). Ritorna un esito testuale.
                val outcome = supabase.postgrest.rpc("delete_own_account").data
                when {
                    outcome.contains("has_linked_patients") -> throw DomainError.StillHasLinkedPatients
                    outcome.contains("not_authenticated") -> throw DomainError.NotAuthenticated
                    else -> Unit // "deleted"
                }
            }.mapToDomainError()
        }

    /**
     * Converte le eccezioni residue (Supabase REST/rete) in [DomainError] generici, così
     * nessun dettaglio tecnico raggiunge la UI. I [DomainError] già lanciati passano invariati.
     */
    private fun <T> Result<T>.mapToDomainError(): Result<T> = recoverCatching { e ->
        throw when (e) {
            is DomainError -> e
            is HttpRequestException -> DomainError.Network
            else -> DomainError.Unknown
        }
    }
}

/**
 * Impacchetta i dati del form come `raw_user_meta_data` del signUp: il trigger
 * `crea_profilo_da_auth` sul DB li legge e crea profilo + riga paziente/specialista
 * nella stessa transazione dell'account (se una insert fallisce, niente utente in limbo).
 * Le chiavi devono combaciare con quelle attese dal trigger.
 */
private fun RegisterData.toAuthMetadata(): JsonObject = buildJsonObject {
    put("codice_fiscale", taxCode.trim().uppercase())
    put("nome", firstName)
    put("cognome", lastName)
    when (this@toAuthMetadata) {
        is RegisterData.PatientData -> {
            put("ruolo", "paziente")
            put("sesso", gender.toDbLabel())
            put("data_nascita", birthDate.toString())
        }
        is RegisterData.SpecialistData -> {
            put("ruolo", "specialista")
            put("partita_iva", vatNumber)
            put("specializzazione", specialization.dbLabel)
            put("citta", city)
        }
    }
}

private fun Gender.toDbLabel(): String = when (this) {
    Gender.M -> "M"
    Gender.F -> "F"
    Gender.OTHER -> "Altro"
}