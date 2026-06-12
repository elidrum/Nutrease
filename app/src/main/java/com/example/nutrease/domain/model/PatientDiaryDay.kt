package com.example.nutrease.domain.model

import kotlinx.datetime.LocalDate

/** Un giorno del diario aggregato per la vista specialista: pasti, sintomi e totali nutrienti. */
data class PatientDiaryDay(
    val date: LocalDate,
    val meals: List<Meal>,
    val symptoms: List<Symptom>,
    val totals: NutrientTotals
) {
    val isEmpty: Boolean get() = meals.isEmpty() && symptoms.isEmpty()
}