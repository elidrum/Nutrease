package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.ReminderConfig
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.ReminderRepository
import javax.inject.Inject

/**
 * Restituisce tutti i promemoria del paziente autenticato, ordinati per orario
 * (e id a parità d'orario), per una visualizzazione stabile nella lista.
 */
class GetReminderConfigsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val reminderRepository: ReminderRepository
) {
    suspend operator fun invoke(): Result<List<ReminderConfig>> = runCatching {
        val user = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Utente non autenticato")
        reminderRepository.getConfigs(user.taxCode).getOrThrow()
            .sortedWith(compareBy({ it.time.toSecondOfDay() }, { it.id ?: Long.MAX_VALUE }))
    }
}
