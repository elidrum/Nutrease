package com.example.nutrease.domain.model

/** Quante volte un tipo di sintomo compare nel periodo (per il "top 3" delle statistiche). */
data class SymptomFrequency(
    val type: SymptomType,
    val count: Int
)