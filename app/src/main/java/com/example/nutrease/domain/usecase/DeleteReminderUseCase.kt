package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.ReminderRepository
import com.example.nutrease.domain.repository.ReminderScheduler
import javax.inject.Inject

/**
 * Elimina un singolo promemoria e ne cancella lo scheduling. La RLS `notifica_self`
 * garantisce che il paziente possa eliminare solo le proprie righe.
 */
class DeleteReminderUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val scheduler: ReminderScheduler
) {
    suspend operator fun invoke(configId: Long): Result<Unit> = runCatching {
        reminderRepository.deleteConfig(configId).getOrThrow()
        scheduler.cancel(configId)
    }
}
