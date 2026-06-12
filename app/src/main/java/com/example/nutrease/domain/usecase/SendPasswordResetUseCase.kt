package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Avvia il reset password ("Password dimenticata?"): valida l'email in locale e invia
 * il codice via Supabase. Il server risponde OK anche se l'email non esiste, così dalla
 * UI non si può scoprire quali indirizzi sono registrati (anti user-enumeration).
 */
class SendPasswordResetUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> = runCatching {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || !trimmed.contains("@")) throw DomainError.InvalidEmail
        authRepository.sendPasswordReset(trimmed).getOrThrow()
    }
}