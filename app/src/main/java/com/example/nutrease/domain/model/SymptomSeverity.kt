package com.example.nutrease.domain.model

/**
 * Severità del sintomo su 4 livelli. Il DB usa `Intensita` 1–10: in scrittura ogni
 * livello mappa su un valore fisso ([intensity]), in lettura [fromIntensity]
 * bucketizza l'intervallo 1–10 nei 4 livelli (regge anche dati inseriti da altri client).
 */
enum class SymptomSeverity(val intensity: Int) {
    NONE(1),
    MILD(3),
    MODERATE(6),
    SEVERE(9);

    companion object {
        fun fromIntensity(value: Int): SymptomSeverity = when {
            value <= 2 -> NONE
            value <= 4 -> MILD
            value <= 7 -> MODERATE
            else -> SEVERE
        }
    }
}
