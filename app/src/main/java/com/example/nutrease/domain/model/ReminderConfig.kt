package com.example.nutrease.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Un singolo promemoria diario. Un paziente può averne più d'uno (es. uno per pasto):
 * ognuno è una riga di `notifica_config` con il proprio insieme di giorni, orario e messaggio.
 */
data class ReminderConfig(
    val id: Long? = null,
    val patientTaxCode: String,
    val enabled: Boolean,
    val daysOfWeek: Set<DayOfWeek>,
    val time: LocalTime,
    val message: String? = null
) {
    companion object {
        const val DEFAULT_MESSAGE = "Ricordati di compilare il tuo diario alimentare!"
        const val MAX_MESSAGE_LENGTH = 200

        /**
         * Set di promemoria "uno per pasto" (colazione/pranzo/cena), tutti i giorni.
         * Usato dall'azione rapida quando la lista è vuota: copre il requisito
         * "almeno un promemoria per ogni pasto".
         */
        fun mealPresets(patientTaxCode: String): List<ReminderConfig> {
            val everyDay = DayOfWeek.entries.toSet()
            return listOf(
                ReminderConfig(patientTaxCode = patientTaxCode, enabled = true, daysOfWeek = everyDay, time = LocalTime(8, 0), message = "Registra la colazione 🍳"),
                ReminderConfig(patientTaxCode = patientTaxCode, enabled = true, daysOfWeek = everyDay, time = LocalTime(13, 0), message = "Registra il pranzo 🍝"),
                ReminderConfig(patientTaxCode = patientTaxCode, enabled = true, daysOfWeek = everyDay, time = LocalTime(20, 0), message = "Registra la cena 🍽️")
            )
        }
    }
}
