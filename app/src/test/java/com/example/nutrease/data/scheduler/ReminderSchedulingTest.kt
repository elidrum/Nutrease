package com.example.nutrease.data.scheduler

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class ReminderSchedulingTest {

    private val tz = TimeZone.UTC
    // 2026-06-08 10:00 UTC. Il giorno della settimana è derivato dalla data, non hardcoded.
    private val now = LocalDateTime(2026, 6, 8, 10, 0)
    private val nowInstant = now.toInstant(tz)

    @Test
    fun `same day later time schedules for today`() {
        val delay = ReminderScheduling.delayUntilNext(now, now.dayOfWeek, 20, 0, tz)
        val expected = LocalDateTime(now.date, LocalTime(20, 0)).toInstant(tz) - nowInstant
        assertEquals(expected.inWholeMilliseconds, delay)
        // sanity: 10:00 -> 20:00 = 10 ore
        assertEquals(10.hours.inWholeMilliseconds, delay)
    }

    @Test
    fun `same day past time rolls to next week`() {
        val delay = ReminderScheduling.delayUntilNext(now, now.dayOfWeek, 8, 0, tz)
        // slot 08:00 di oggi è già passato (sono le 10:00) -> +7 giorni
        val expected = 7.days - 2.hours
        assertEquals(expected.inWholeMilliseconds, delay)
    }

    @Test
    fun `target always lands on the requested day and time`() {
        // Proprietà chiave (bug "giorni non rispettati"): per OGNI giorno richiesto lo slot
        // calcolato cade esattamente su quel giorno, mai un altro.
        for (day in DayOfWeek.entries) {
            val delay = ReminderScheduling.delayUntilNext(now, day, 9, 30, tz)
            val target = (nowInstant + delay.milliseconds).toLocalDateTime(tz)
            assertEquals("giorno errato per $day", day, target.dayOfWeek)
            assertEquals("orario errato per $day", LocalTime(9, 30), target.time)
            assertTrue("delay fuori range per $day", delay in 0..7.days.inWholeMilliseconds)
        }
    }

    @Test
    fun `delay is strictly within one week`() {
        DayOfWeek.entries.forEach { day ->
            val delay = ReminderScheduling.delayUntilNext(now, day, 23, 59, tz)
            assertTrue(delay > 0)
            assertTrue(delay <= 7.days.inWholeMilliseconds)
        }
    }
}
