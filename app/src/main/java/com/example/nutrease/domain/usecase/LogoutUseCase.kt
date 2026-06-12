package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.AuthRepository
import javax.inject.Inject

/** Chiusura della sessione corrente (RF4). */
class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.logout()
}