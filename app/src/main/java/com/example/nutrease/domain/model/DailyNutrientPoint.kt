package com.example.nutrease.domain.model

import kotlinx.datetime.LocalDate

/** Punto (giorno, valore) di una serie temporale del grafico nutrienti (RF21). */
data class DailyNutrientPoint(
    val date: LocalDate,
    val value: Double
)