package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.PasswordPolicy
import com.example.nutrease.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Completa il reset password: valida codice (6 cifre) e nuova password (PasswordPolicy), poi
 * delega al repository la verifica OTP + set della password. Validazioni a basso costo prima
 * della rete, coerenti con [ChangePasswordUseCase].
 */
class ConfirmPasswordResetUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        email: String,
        code: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        val trimmedCode = code.trim()
        require(trimmedCode.length == 6 && trimmedCode.all { it.isDigit() }) {
            "Inserisci il codice di 6 cifre ricevuto via email"
        }
        PasswordPolicy.validate(newPassword)?.let { throw IllegalArgumentException(it.message) }
        authRepository.confirmPasswordReset(email, trimmedCode, newPassword).getOrThrow()
    }
}
