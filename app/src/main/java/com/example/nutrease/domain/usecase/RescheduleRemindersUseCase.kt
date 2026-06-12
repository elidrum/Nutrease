package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.ReminderRepository
import com.example.nutrease.domain.repository.ReminderScheduler
import javax.inject.Inject

/**
 * Ri-pianifica tutti i promemoria abilitati del paziente corrente. Gli allarmi esatti di
 * AlarmManager si perdono al riavvio del dispositivo (e quando l'app viene terminata prima dello
 * sparo): vanno riarmati. Invocata all'avvio dell'app dopo l'auth (più affidabile di un
 * BootReceiver, che girerebbe senza rete/sessione garantite). Idempotente: [ReminderScheduler.schedule]
 * sostituisce gli allarmi già presenti.
 */
class RescheduleRemindersUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val reminderRepository: ReminderRepository,
    private val scheduler: ReminderScheduler
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val user = authRepository.getCurrentUser() ?: return@runCatching
        if (user.role != UserRole.PATIENT) return@runCatching // solo i pazienti hanno promemoria
        reminderRepository.getConfigs(user.taxCode).getOrThrow()
            .filter { it.enabled && it.id != null }
            .forEach { scheduler.schedule(it) }
    }
}
