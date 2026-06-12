package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Accesso email+password (RF3). Come tutti gli UseCase del progetto espone
 * `operator fun invoke`: il ViewModel lo chiama come una funzione (`loginUseCase(email,
 * password)`), a rimarcare che uno UseCase = una sola azione di dominio.
 */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthUser> =
        repository.login(email, password)
}