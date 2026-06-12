package com.example.nutrease.domain.model

/**
 * Stato di una richiesta di collegamento; [dbLabel] è l'etichetta italiana esatta del DB.
 * Qui un'etichetta ignota fa fallire subito ([fromDbLabel] lancia): lo stato è un dato
 * vincolato da CHECK, se non combacia c'è un bug.
 */
enum class LinkRequestStatus(val dbLabel: String) {
    PENDING("In Attesa"),
    ACCEPTED("Accettata"),
    REJECTED("Rifiutata");

    companion object {
        fun fromDbLabel(label: String): LinkRequestStatus =
            entries.firstOrNull { it.dbLabel == label }
                ?: throw IllegalArgumentException("Stato richiesta sconosciuto: $label")
    }
}