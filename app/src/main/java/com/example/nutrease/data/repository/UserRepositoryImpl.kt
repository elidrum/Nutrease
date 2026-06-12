package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.PatientDto
import com.example.nutrease.data.dto.SpecialistDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.model.Patient
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementazione di [UserRepository] su PostgREST: lettura/aggiornamento di
 * `paziente`/`specialista` (gli UPDATE elencano le colonne modificabili: il codice
 * fiscale, chiave, non si tocca) e cambio password via Supabase Auth.
 */
class UserRepositoryImpl(
    private val supabase: SupabaseClient
) : UserRepository {

    override suspend fun getPatient(taxCode: String): Result<Patient> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("paziente")
                    .select { filter { eq("CodiceFiscale", taxCode) } }
                    .decodeSingle<PatientDto>()
                    .toDomain()
            }
        }

    override suspend fun getSpecialist(taxCode: String): Result<Specialist> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("specialista")
                    .select { filter { eq("CodiceFiscale", taxCode) } }
                    .decodeSingle<SpecialistDto>()
                    .toDomain()
            }
        }

    override suspend fun updatePatient(patient: Patient): Result<Unit> = ioResult {
        supabase.from("paziente").update({
            set("Nome", patient.firstName)
            set("Cognome", patient.lastName)
            set("Telefono", patient.phone)
            set("Email", patient.email)
            set("Via", patient.street)
            set("Citta", patient.city)
            set("CAP", patient.postalCode)
        }) {
            filter { eq("CodiceFiscale", patient.taxCode) }
        }
    }

    override suspend fun updateSpecialist(specialist: Specialist): Result<Unit> = ioResult {
        supabase.from("specialista").update({
            set("Nome", specialist.firstName)
            set("Cognome", specialist.lastName)
            set("Telefono", specialist.phone)
            set("Email", specialist.email)
            set("Info", specialist.info)
            set("Specializzazione", specialist.specialization?.dbLabel)
            set("Citta", specialist.city)
        }) {
            filter { eq("CodiceFiscale", specialist.taxCode) }
        }
    }

    override suspend fun changePassword(newPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.auth.updateUser { password = newPassword }
                Unit
            }.recoverCatching { e ->
                // Mappa gli errori GoTrue su DomainError: la UI non vede mai il testo
                // grezzo. Caso tipico: stessa password → AuthErrorCode.SamePassword.
                throw when {
                    e is AuthRestException && e.errorCode == AuthErrorCode.SamePassword ->
                        DomainError.SamePassword
                    e is HttpRequestException -> DomainError.Network
                    else -> DomainError.Unknown
                }
            }
        }

    private suspend fun ioResult(block: suspend () -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching { block() } }
}