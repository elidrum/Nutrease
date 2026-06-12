package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.DomainError
import com.example.nutrease.domain.repository.AuthRepository
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    /**
     * Elimina l'account (RF7): conferma la password (re-auth), poi delega la
     * cancellazione al server ([AuthRepository.deleteAccount], RPC SECURITY DEFINER)
     * che rimuove dati di dominio + `auth.users`. Infine fa logout (best-effort: la
     * sessione lato server è già invalidata dalla cancellazione dell'auth user, ma
     * vogliamo comunque ripulire quella locale). Email e ruolo vengono dall'utente
     * corrente, non dalla UI.
     */
    suspend operator fun invoke(password: String): Result<Unit> = runCatching {
        val user = authRepository.getCurrentUser() ?: throw DomainError.NotAuthenticated
        authRepository.reauthenticate(user.email, password).getOrThrow()
        authRepository.deleteAccount().getOrThrow()
        // logout best-effort: la sessione server è già invalidata dalla delete, quindi
        // ignoriamo l'esito (getOrDefault) senza far fallire l'operazione. È anche l'ultima
        // espressione del runCatching → la lambda ritorna Unit (Result<Unit>).
        runCatching { authRepository.logout() }.getOrDefault(Unit)
    }
}
