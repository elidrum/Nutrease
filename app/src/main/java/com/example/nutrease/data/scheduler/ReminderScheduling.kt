package com.example.nutrease.data.scheduler

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * Logica pura (senza dipendenze Android) per lo scheduling dei promemoria: calcolo del
 * prossimo slot settimanale. Separata da [AlarmManagerReminderScheduler] per essere
 * testabile come unit test JVM e per non mescolare matematica del calendario con le API
 * di sistema.
 */
object ReminderScheduling {

    /**
     * Millisecondi dal [now] al prossimo slot (giorno della settimana + ora + minuto)
     * nel fuso [tz]. Se lo slot di oggi è già passato, salta alla settimana successiva.
     * Garantisce che il target cada esattamente sul [day] richiesto (no "giorni sbagliati").
     */
    fun delayUntilNext(
        now: LocalDateTime,
        day: DayOfWeek,
        hour: Int,
        minute: Int,
        tz: TimeZone
    ): Long {
        val daysAhead = ((day.isoDayNumber - now.dayOfWeek.isoDayNumber) + 7) % 7
        val targetDate = now.date.plus(daysAhead, DateTimeUnit.DAY)
        val targetLdt = LocalDateTime(targetDate, LocalTime(hour, minute))
        var targetInstant = targetLdt.toInstant(tz)
        val nowInstant = now.toInstant(tz)
        if (targetInstant <= nowInstant) {
            targetInstant = targetInstant.plus(7, DateTimeUnit.DAY, tz)
        }
        return (targetInstant - nowInstant).inWholeMilliseconds
    }

    private val DayOfWeek.isoDayNumber: Int
        get() = ordinal + 1
}
