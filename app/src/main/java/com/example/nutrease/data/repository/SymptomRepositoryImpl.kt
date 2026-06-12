package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.SymptomDto
import com.example.nutrease.data.dto.toDbDescription
import com.example.nutrease.data.dto.toSymptomType
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.model.SymptomType
import com.example.nutrease.domain.repository.SymptomRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Implementazione di [SymptomRepository] su PostgREST (tabella `sintomo`). I mapper in
 * fondo traducono le due rappresentazioni: `SymptomType` ↔ testo italiano `Descrizione`
 * e `SymptomSeverity` ↔ `Intensita` 1–10 (vedi i KDoc dei rispettivi enum).
 */
class SymptomRepositoryImpl(
    private val supabase: SupabaseClient
) : SymptomRepository {

    override suspend fun getSymptomsForDate(
        fascicoloId: Int,
        date: LocalDate
    ): Result<List<Symptom>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("sintomo")
                .select {
                    filter {
                        eq("IdFascicolo", fascicoloId)
                        eq("Data", date.toString())
                    }
                    order("Ora", Order.ASCENDING)
                }
                .decodeList<SymptomDto>()
                .map { it.toDomain() }
        }
    }

    override suspend fun getSymptom(symptomId: Long): Result<Symptom> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("sintomo")
                    .select { filter { eq("IdSintomo", symptomId) } }
                    .decodeSingle<SymptomDto>()
                    .toDomain()
            }
        }

    override suspend fun addSymptom(symptom: Symptom): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dto = symptom.toDto()
                supabase.from("sintomo")
                    .insert(dto) { select() }
                    .decodeSingle<SymptomDto>()
                    .id
                    ?: throw IllegalStateException("Inserimento sintomo: ID non restituito")
            }
        }

    override suspend fun updateSymptom(symptom: Symptom): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val symptomId = symptom.id
                    ?: throw IllegalArgumentException("Sintomo senza ID: impossibile aggiornare")
                supabase.from("sintomo").update({
                    set("Data", symptom.date.toString())
                    set("Ora", symptom.time.toDbString())
                    set("Descrizione", symptom.dbDescription())
                    set("Intensita", symptom.severity.intensity)
                }) {
                    filter { eq("IdSintomo", symptomId) }
                }
                Unit
            }
        }

    override suspend fun deleteSymptom(symptomId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("sintomo")
                    .delete { filter { eq("IdSintomo", symptomId) } }
                Unit
            }
        }
}

private fun Symptom.toDto(): SymptomDto = SymptomDto(
    id = id,
    fascicoloId = fascicoloId,
    date = date.toString(),
    time = time.toDbString(),
    description = dbDescription(),
    intensity = severity.intensity
)

/**
 * Testo da salvare in `Descrizione`: per i tipi noti l'etichetta italiana; per "Altro"
 * la nota libera dell'utente (se valorizzata), così il dettaglio non va perso. Fallback
 * sull'etichetta "Altro" se la nota è vuota.
 */
private fun Symptom.dbDescription(): String =
    if (type == SymptomType.OTHER && !note.isNullOrBlank()) note.trim()
    else type.toDbDescription()

private fun SymptomDto.toDomain(): Symptom {
    val type = description.toSymptomType()
    // In lettura, una descrizione non riconosciuta ricade su OTHER: quel testo è la nota
    // libera originale. L'etichetta fissa "Altro" non è una nota, quindi resta null.
    val note = if (type == SymptomType.OTHER && description != "Altro") description else null
    return Symptom(
        id = id,
        fascicoloId = fascicoloId,
        date = LocalDate.parse(date),
        time = parseDbTime(time),
        type = type,
        severity = SymptomSeverity.fromIntensity(intensity),
        note = note
    )
}

private fun LocalTime.toDbString(): String {
    val h = hour.toString().padStart(2, '0')
    val m = minute.toString().padStart(2, '0')
    val s = second.toString().padStart(2, '0')
    return "$h:$m:$s"
}

private fun parseDbTime(raw: String): LocalTime {
    val withoutMicros = raw.substringBefore('.')
    return LocalTime.parse(withoutMicros)
}