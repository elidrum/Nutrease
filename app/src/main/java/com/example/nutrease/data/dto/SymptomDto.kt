package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.SymptomType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella `sintomo`. Sotto, i mapper tra [SymptomType] e il testo italiano
 * di `Descrizione`: in lettura i testi non riconosciuti ricadono su OTHER (il campo è
 * varchar libero, non un enum DB).
 */
@Serializable
data class SymptomDto(
    @SerialName("IdSintomo") val id: Long? = null,
    @SerialName("IdFascicolo") val fascicoloId: Int,
    @SerialName("Data") val date: String,
    @SerialName("Ora") val time: String,
    @SerialName("Descrizione") val description: String,
    @SerialName("Intensita") val intensity: Int
)

fun SymptomType.toDbDescription(): String = when (this) {
    SymptomType.BLOATING -> "Gonfiore"
    SymptomType.CRAMPS -> "Crampi"
    SymptomType.DIARRHEA -> "Diarrea"
    SymptomType.CONSTIPATION -> "Stitichezza"
    SymptomType.NAUSEA -> "Nausea"
    SymptomType.REFLUX -> "Reflusso"
    SymptomType.OTHER -> "Altro"
}

fun String.toSymptomType(): SymptomType = when (this) {
    "Gonfiore" -> SymptomType.BLOATING
    "Crampi" -> SymptomType.CRAMPS
    "Diarrea" -> SymptomType.DIARRHEA
    "Stitichezza" -> SymptomType.CONSTIPATION
    "Nausea" -> SymptomType.NAUSEA
    "Reflusso" -> SymptomType.REFLUX
    else -> SymptomType.OTHER
}