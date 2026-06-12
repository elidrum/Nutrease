package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.ReminderConfig
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.ReminderRepository
import com.example.nutrease.domain.repository.ReminderScheduler
import javax.inject.Inject

/**
 * Salva (insert/update) un singolo promemoria e ne riallinea lo scheduling in modo
 * idempotente: prima cancella le schedulazioni del promemoria, poi le ricrea se è
 * abilitato. Cancellare-poi-pianificare per il solo `id` garantisce che, riducendo i
 * giorni o disattivandolo, non restino notifiche "orfane" su giorni non più selezionati.
 */
class SaveReminderConfigUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val reminderRepository: ReminderRepository,
    private val scheduler: ReminderScheduler
) {
    suspend operator fun invoke(config: ReminderConfig): Result<ReminderConfig> = runCatching {
        require(config.daysOfWeek.isNotEmpty()) {
            "Seleziona almeno un giorno della settimana"
        }
        val user = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Utente non autenticato")
        val normalized = config.copy(patientTaxCode = user.taxCode)
        val saved = reminderRepository.saveConfig(normalized).getOrThrow()
        saved.id?.let { scheduler.cancel(it) }
        if (saved.enabled) scheduler.schedule(saved)
        saved
    }
}