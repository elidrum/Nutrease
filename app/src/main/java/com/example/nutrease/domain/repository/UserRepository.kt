package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.Patient
import com.example.nutrease.domain.model.Specialist

/**
 * Contratto del dominio per lettura/aggiornamento dei profili anagrafici (RF5–RF6).
 * Le creazioni NON passano da qui: profilo e riga paziente/specialista nascono da un
 * trigger sul DB durante la registrazione (vedi [AuthRepository.register]).
 */
interface UserRepository {
    suspend fun getPatient(taxCode: String): Result<Patient>
    suspend fun getSpecialist(taxCode: String): Result<Specialist>
    suspend fun updatePatient(patient: Patient): Result<Unit>
    suspend fun updateSpecialist(specialist: Specialist): Result<Unit>

    /** Imposta la nuova password dell'utente loggato; richiede la re-auth a monte (RF6). */
    suspend fun changePassword(newPassword: String): Result<Unit>
    // L'eliminazione account è server-side via AuthRepository.deleteAccount (RPC
    // SECURITY DEFINER): il client non ha policy DELETE su paziente/specialista.
}