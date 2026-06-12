package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.ReminderConfig

/**
 * Astrazione sopra il sistema di scheduling di notifiche locali. L'implementazione
 * concreta vive in `data/` per non contaminare `domain/` con dipendenze Android:
 * così gli UseCase pianificano promemoria senza vedere AlarmManager.
 *
 * Lo scheduling è "per promemoria": ogni [ReminderConfig] (identificato dal suo `id`)
 * ha le proprie schedulazioni indipendenti, una per giorno selezionato. Questo permette
 * più promemoria attivi contemporaneamente senza che si sovrascrivano.
 */
interface ReminderScheduler {
    /**
     * Pianifica una notifica ricorrente per ciascun giorno della settimana indicato
     * in [config], all'orario specificato. [config] deve avere `id` non nullo (post-salvataggio).
     * Sostituisci-prima-di-pianificare è responsabilità del chiamante (vedi [cancel]).
     */
    fun schedule(config: ReminderConfig)

    /** Cancella le notifiche del solo promemoria [configId] (tutti i suoi giorni). */
    fun cancel(configId: Long)

    /** Cancella tutte le notifiche pianificate dal sistema dei promemoria. */
    fun cancelAll()
}
