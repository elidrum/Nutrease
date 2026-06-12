package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.model.PasswordPolicy
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.UserRepository
import javax.inject.Inject

class ChangePasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    /**
     * Cambia la password (RF6): valida la nuova password, ri-verifica la
     * password corrente (re-auth) e solo allora aggiorna l'utente.
     */
    suspend operator fun invoke(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        val user = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        PasswordPolicy.validate(newPassword)?.let { throw IllegalArgumentException(it.message) }
        // Guard locale: evita il giro di rete (e l'errore grezzo di GoTrue) quando la nuova
        // password coincide con l'attuale. Il repository mappa comunque lo stesso caso lato server.
        if (newPassword == currentPassword) throw DomainError.SamePassword
        authRepository.reauthenticate(user.email, currentPassword).getOrThrow()
        userRepository.changePassword(newPassword).getOrThrow()
    }
}