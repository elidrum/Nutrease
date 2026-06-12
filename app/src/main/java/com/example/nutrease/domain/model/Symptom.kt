package com.example.nutrease.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Sintomo registrato nel diario (tabella `sintomo`): data+ora, tipologia e severità (RF10).
 * [note] è il testo libero usato quando [type] è [SymptomType.OTHER]: per gli altri tipi
 * resta null. Viene persistito nella colonna `Descrizione` (varchar libero) al posto
 * dell'etichetta fissa "Altro".
 */
data class Symptom(
    val id: Long?,
    val fascicoloId: Int,
    val date: LocalDate,
    val time: LocalTime,
    val type: SymptomType,
    val severity: SymptomSeverity,
    val note: String? = null
)
