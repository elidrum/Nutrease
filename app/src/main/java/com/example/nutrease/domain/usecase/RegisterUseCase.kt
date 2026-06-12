package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.RegisterData
import com.example.nutrease.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Registrazione di un nuovo paziente o specialista (RF1/RF2). Il ramo è deciso dal
 * sottotipo di [RegisterData]; la creazione del profilo avviene in modo atomico sul
 * server (vedi [AuthRepository.register]).
 */
class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(data: RegisterData): Result<Unit> =
        authRepository.register(data)
}