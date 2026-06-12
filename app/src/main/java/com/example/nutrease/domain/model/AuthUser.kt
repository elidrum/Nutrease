package com.example.nutrease.domain.model

/**
 * Utente autenticato così come lo vede il dominio: l'id della sessione Supabase ([id]),
 * più il ruolo e il codice fiscale letti da `profilo_utente`. È il "chi sono" minimo che
 * serve a UseCase e ViewModel per autorizzare le operazioni e instradare alla home giusta.
 */
data class AuthUser(
    val id: String,
    val email: String,
    val role: UserRole,
    val taxCode: String
)