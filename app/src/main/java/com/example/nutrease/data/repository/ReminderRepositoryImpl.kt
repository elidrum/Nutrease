package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.ReminderConfigDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.data.dto.toDto
import com.example.nutrease.domain.model.ReminderConfig
import com.example.nutrease.domain.repository.ReminderRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementazione di [ReminderRepository] su PostgREST (`notifica_config`).
 * `saveConfig` distingue insert/update dall'id: null = riga nuova (id generato dal DB),
 * valorizzato = update mirato. Le RLS limitano ogni paziente alle proprie righe.
 */
class ReminderRepositoryImpl(
    private val supabase: SupabaseClient
) : ReminderRepository {

    override suspend fun getConfigs(patientTaxCode: String): Result<List<ReminderConfig>> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("notifica_config")
                    .select {
                        filter { eq("CodFiscalePaziente", patientTaxCode) }
                        order("IdConfig", Order.ASCENDING)
                    }
                    .decodeList<ReminderConfigDto>()
                    .map { it.toDomain() }
            }
        }

    override suspend fun saveConfig(config: ReminderConfig): Result<ReminderConfig> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dto = config.toDto()
                val saved = if (config.id != null) {
                    supabase.from("notifica_config").update({
                        set("Attiva", dto.active)
                        set("GiorniSettimana", dto.daysOfWeek)
                        set("Orario", dto.time)
                        set("Messaggio", dto.message)
                    }) {
                        filter { eq("IdConfig", config.id) }
                        select()
                    }.decodeSingle<ReminderConfigDto>()
                } else {
                    // id == null ⇒ encodeDefaults=false omette "IdConfig" → bigserial generato dal DB.
                    supabase.from("notifica_config")
                        .insert(dto) { select() }
                        .decodeSingle<ReminderConfigDto>()
                }
                saved.toDomain()
            }
        }

    override suspend fun deleteConfig(configId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("notifica_config")
                    .delete { filter { eq("IdConfig", configId) } }
                Unit
            }
        }
}
