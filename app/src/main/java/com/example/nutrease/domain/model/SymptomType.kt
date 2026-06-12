package com.example.nutrease.domain.model

/**
 * Tipologia di sintomo gastrointestinale (RF10). Sul DB finisce come testo italiano
 * nella colonna `Descrizione`; l'ordine di dichiarazione fa anche da tie-break
 * deterministico nella classifica "sintomi più frequenti" delle statistiche.
 */
enum class SymptomType {
    BLOATING,
    CRAMPS,
    DIARRHEA,
    CONSTIPATION,
    NAUSEA,
    REFLUX,
    OTHER
}
