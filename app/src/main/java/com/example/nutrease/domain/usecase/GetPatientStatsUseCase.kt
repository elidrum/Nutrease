package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.DailyNutrientPoint
import com.example.nutrease.domain.model.DiaryDateRange
import com.example.nutrease.domain.model.NutrientFilter
import com.example.nutrease.domain.model.PatientDiaryDay
import com.example.nutrease.domain.model.PatientStats
import com.example.nutrease.domain.model.SymptomFrequency
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import kotlinx.datetime.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/**
 * Statistiche del paziente sugli ultimi 30 giorni (RF21). Zero query nuove: è una
 * trasformazione pura sopra [GetPatientDiaryRangeUseCase] (composizione di UseCase).
 * Calcola in un colpo solo le 4 serie nutriente (così il cambio chip nel grafico non
 * rifà nulla), il top 3 sintomi (tie-break deterministico su `ordinal` a parità di
 * conteggio) e la % di giorni con almeno un sintomo.
 */
class GetPatientStatsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val getPatientDiaryRangeUseCase: GetPatientDiaryRangeUseCase
) {
    suspend operator fun invoke(
        fascicoloId: Int,
        today: LocalDate
    ): Result<PatientStats> = try {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.SPECIALIST) {
            throw IllegalStateException("Funzione disponibile solo per gli specialisti")
        }

        val days = getPatientDiaryRangeUseCase(
            fascicoloId = fascicoloId,
            range = DiaryDateRange.Last30Days,
            today = today
        ).getOrThrow()

        val ascending = days.sortedBy { it.date }

        Result.success(
            PatientStats(
                bmi = null, // richiede peso/altezza (modulo misurazioni, post-MVP); la UI mostra il placeholder
                dailySeriesByFilter = buildNutrientSeries(ascending),
                topSymptoms = computeTopSymptoms(ascending),
                symptomaticDaysPercent = computeSymptomaticDaysPercent(ascending),
                totalDays = ascending.size
            )
        )
    } catch (e: CancellationException) {
        // Come in GetPatientDiaryRangeUseCase: la cancellazione non è un errore di
        // caricamento, va propagata e non incapsulata in Result.failure.
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private fun buildNutrientSeries(
        days: List<PatientDiaryDay>
    ): Map<NutrientFilter, List<DailyNutrientPoint>> = mapOf(
        NutrientFilter.LACTOSE to days.map { DailyNutrientPoint(it.date, it.totals.lactose) },
        NutrientFilter.SORBITOL to days.map { DailyNutrientPoint(it.date, it.totals.sorbitol) },
        NutrientFilter.GLUTEN to days.map { DailyNutrientPoint(it.date, it.totals.gluten) },
        NutrientFilter.CALORIES to days.map { DailyNutrientPoint(it.date, it.totals.calories) }
    )

    private fun computeTopSymptoms(days: List<PatientDiaryDay>): List<SymptomFrequency> =
        days.flatMap { it.symptoms }
            .groupingBy { it.type }
            .eachCount()
            .map { (type, count) -> SymptomFrequency(type, count) }
            .sortedWith(compareByDescending<SymptomFrequency> { it.count }.thenBy { it.type.ordinal })
            .take(TOP_SYMPTOMS_LIMIT)

    private fun computeSymptomaticDaysPercent(days: List<PatientDiaryDay>): Double {
        if (days.isEmpty()) return 0.0
        val symptomatic = days.count { it.symptoms.isNotEmpty() }
        return symptomatic.toDouble() / days.size * 100.0
    }

    companion object {
        const val TOP_SYMPTOMS_LIMIT = 3
    }
}