package com.example.nutrease.domain.model

/**
 * Specializzazione dello specialista (enum Postgres `tipo_specializ_enum`). [dbLabel]
 * è l'etichetta italiana esatta usata dal DB; [fromDbLabel] è tollerante (null per
 * etichette ignote) perché la colonna è facoltativa nei profili storici.
 */
enum class SpecializationType(val dbLabel: String) {
    NUTRIZIONISTA("Nutrizionista"),
    DIETISTA("Dietista"),
    GASTROENTEROLOGO("Gastroenterologo");

    companion object {
        fun fromDbLabel(label: String?): SpecializationType? =
            label?.let { value -> entries.firstOrNull { it.dbLabel == value } }
    }
}