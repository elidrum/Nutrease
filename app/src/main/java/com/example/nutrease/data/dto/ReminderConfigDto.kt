package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.ReminderConfig
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella `notifica_config`. I giorni sono un `integer[]` Postgres con
 * numerazione ISO (1=lunedì … 7=domenica): i mapper sotto lo traducono in
 * `Set<DayOfWeek>` e viceversa, scartando eventuali valori fuori range.
 */
@Serializable
data class ReminderConfigDto(
    @SerialName("IdConfig") val id: Long? = null,
    @SerialName("CodFiscalePaziente") val patientTaxCode: String,
    @SerialName("Attiva") val active: Boolean,
    @SerialName("GiorniSettimana") val daysOfWeek: List<Int>,
    @SerialName("Orario") val time: String,
    @SerialName("Messaggio") val message: String? = null
)

fun ReminderConfigDto.toDomain(): ReminderConfig = ReminderConfig(
    id = id,
    patientTaxCode = patientTaxCode,
    enabled = active,
    daysOfWeek = daysOfWeek.mapNotNull { iso -> iso.toDayOfWeekOrNull() }.toSet(),
    time = parseDbTime(time),
    message = message
)

fun ReminderConfig.toDto(): ReminderConfigDto = ReminderConfigDto(
    id = id,
    patientTaxCode = patientTaxCode,
    active = enabled,
    daysOfWeek = daysOfWeek.map { it.isoDayNumber }.sorted(),
    time = time.toDbString(),
    message = message
)

private fun Int.toDayOfWeekOrNull(): DayOfWeek? =
    if (this in 1..7) DayOfWeek.entries[this - 1] else null

private val DayOfWeek.isoDayNumber: Int
    get() = ordinal + 1

private fun LocalTime.toDbString(): String {
    val h = hour.toString().padStart(2, '0')
    val m = minute.toString().padStart(2, '0')
    return "$h:$m:00"
}

private fun parseDbTime(raw: String): LocalTime {
    val withoutMicros = raw.substringBefore('.')
    return LocalTime.parse(withoutMicros)
}