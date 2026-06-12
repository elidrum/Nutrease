package com.example.nutrease.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearsUntil

/**
 * Regola di età minima per la registrazione (RF2): di default 18 anni.
 *
 * Funzione pura e testabile (solo kotlinx-datetime, nessun `java.time`), sul modello di
 * [PasswordPolicy]. La data odierna è un parametro esplicito così che il dominio resti
 * privo di dipendenze sull'orologio di sistema e i test siano deterministici; chi chiama
 * passa "oggi". Il limite vive in [MIN_AGE_YEARS], facilmente modificabile.
 */
object AgePolicy {
    const val MIN_AGE_YEARS = 18

    /** Anni compiuti a [today] da chi è nato in [birthDate] (negativo se data futura). */
    fun ageOn(birthDate: LocalDate, today: LocalDate): Int = birthDate.yearsUntil(today)

    /** `null` se l'età è sufficiente, altrimenti l'errore di dominio. */
    fun validate(birthDate: LocalDate, today: LocalDate): AgeValidationError? =
        if (ageOn(birthDate, today) < MIN_AGE_YEARS) AgeValidationError.TOO_YOUNG else null
}

enum class AgeValidationError(val message: String) {
    TOO_YOUNG("Devi avere almeno ${AgePolicy.MIN_AGE_YEARS} anni per registrarti")
}
