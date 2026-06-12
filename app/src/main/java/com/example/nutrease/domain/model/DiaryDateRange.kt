package com.example.nutrease.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Periodo selezionabile nella vista diario dello specialista (RF20). I preset sono
 * relativi a "oggi", che è un parametro di [resolve] (niente orologio nel dominio:
 * stesso principio di AgePolicy, test deterministici). Custom normalizza estremi invertiti.
 */
sealed class DiaryDateRange {

    /** Concretizza il periodo in un intervallo di date chiuso, estremi inclusi. */
    abstract fun resolve(today: LocalDate): ClosedRange<LocalDate>

    data object Today : DiaryDateRange() {
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> = today..today
    }

    data object Last7Days : DiaryDateRange() {
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> =
            today.minus(6, DateTimeUnit.DAY)..today
    }

    data object Last30Days : DiaryDateRange() {
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> =
            today.minus(29, DateTimeUnit.DAY)..today
    }

    data class Custom(val start: LocalDate, val end: LocalDate) : DiaryDateRange() {
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> {
            val (lo, hi) = if (start <= end) start to end else end to start
            return lo..hi
        }
    }
}