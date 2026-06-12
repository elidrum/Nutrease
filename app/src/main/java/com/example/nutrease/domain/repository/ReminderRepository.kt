package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.ReminderConfig

interface ReminderRepository {
    /** Tutti i promemoria del paziente (può esserne più d'uno). */
    suspend fun getConfigs(patientTaxCode: String): Result<List<ReminderConfig>>

    /** Inserisce ([ReminderConfig.id] null) o aggiorna ([ReminderConfig.id] valorizzato) un promemoria. */
    suspend fun saveConfig(config: ReminderConfig): Result<ReminderConfig>

    /** Elimina il singolo promemoria identificato da [configId]. */
    suspend fun deleteConfig(configId: Long): Result<Unit>
}
