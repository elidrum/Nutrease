package com.example.nutrease.domain.model

/**
 * Regole di robustezza della password (RF1/RF2): minimo 8 caratteri, almeno una
 * lettera maiuscola e almeno una cifra.
 *
 * Funzione pura e testabile, riusata sia dalla registrazione sia dal cambio
 * password. Il messaggio italiano è allegato all'errore (coerente con gli altri
 * messaggi di dominio del progetto) così che UI e UseCase non lo duplichino.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 8

    fun validate(password: String): PasswordValidationError? = when {
        password.length < MIN_LENGTH -> PasswordValidationError.TOO_SHORT
        password.none { it.isUpperCase() } -> PasswordValidationError.NO_UPPERCASE
        password.none { it.isDigit() } -> PasswordValidationError.NO_DIGIT
        else -> null
    }
}

enum class PasswordValidationError(val message: String) {
    TOO_SHORT("La password deve contenere almeno ${PasswordPolicy.MIN_LENGTH} caratteri"),
    NO_UPPERCASE("La password deve contenere almeno una lettera maiuscola"),
    NO_DIGIT("La password deve contenere almeno una cifra")
}