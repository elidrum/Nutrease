package com.example.nutrease.domain.model

/**
 * Statistiche del paziente su 30 giorni (RF21). [dailySeriesByFilter] contiene già le
 * 4 serie (una per nutriente): cambiare chip nel grafico è un semplice lookup, nessun
 * re-fetch. [bmi] è null finché non esiste il modulo misurazioni (la UI mostra il placeholder).
 */
data class PatientStats(
    val bmi: BmiResult?,
    val dailySeriesByFilter: Map<NutrientFilter, List<DailyNutrientPoint>>,
    val topSymptoms: List<SymptomFrequency>,
    val symptomaticDaysPercent: Double,
    val totalDays: Int
)