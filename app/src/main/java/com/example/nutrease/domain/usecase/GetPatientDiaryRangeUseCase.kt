package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.DiaryDateRange
import com.example.nutrease.domain.model.NutrientTotals
import com.example.nutrease.domain.model.PatientDiaryDay
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.DiaryRepository
import com.example.nutrease.domain.repository.SymptomRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/**
 * Diario di un paziente su un intervallo di date, per la vista dello specialista
 * (RF19–RF20). Riusa i repository del diario del paziente: sono già parametrici sul
 * fascicolo e le RLS sul DB autorizzano la lettura allo specialista collegato.
 *
 * Concorrenza strutturata: ogni giorno richiede 2 chiamate (pasti+sintomi), quindi i
 * giorni si caricano in parallelo con `async` dentro `coroutineScope` e si attendono
 * con `awaitAll`. Se un giorno fallisce, `coroutineScope` cancella i fratelli ancora
 * in volo e l'errore risale come `Result.failure` (niente risultati parziali).
 *
 * Il cap [MAX_RANGE_DAYS] limita il fan-out: 92 giorni = ~184 richieste già al limite;
 * un intervallo arbitrario potrebbe saturare il pool di connessioni HTTP.
 */
class GetPatientDiaryRangeUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val diaryRepository: DiaryRepository,
    private val symptomRepository: SymptomRepository
) {
    suspend operator fun invoke(
        fascicoloId: Int,
        range: DiaryDateRange,
        today: LocalDate
    ): Result<List<PatientDiaryDay>> = try {
        val authUser = authRepository.getCurrentUser()
            ?: throw IllegalStateException("Nessun utente autenticato")
        if (authUser.role != UserRole.SPECIALIST) {
            throw IllegalStateException("Funzione disponibile solo per gli specialisti")
        }

        val resolved = range.resolve(today)
        val days = resolved.start.daysUntil(resolved.endInclusive) + 1
        require(days in 1..MAX_RANGE_DAYS) {
            "Il periodo selezionato non può superare $MAX_RANGE_DAYS giorni"
        }

        val diaryDays = coroutineScope {
            (0 until days)
                .map { offset -> resolved.start.plus(offset, DateTimeUnit.DAY) }
                .map { date ->
                    async {
                        val meals = diaryRepository.getMealsForDate(fascicoloId, date).getOrThrow()
                        val symptoms = symptomRepository.getSymptomsForDate(fascicoloId, date).getOrThrow()
                        val totals = meals.fold(NutrientTotals.Zero) { acc, meal -> acc + meal.totals }
                        PatientDiaryDay(date = date, meals = meals, symptoms = symptoms, totals = totals)
                    }
                }
                .awaitAll()
                .sortedByDescending { it.date }
        }
        Result.success(diaryDays)
    } catch (e: CancellationException) {
        // La cancellazione (es. lo specialista cambia periodo mentre il fetch è ancora
        // in volo) non è un errore: va ri-lanciata, non incapsulata in Result.failure.
        // Altrimenti il messaggio "StandaloneCoroutine was cancelled" risalirebbe fino
        // alla UI come se fosse un errore reale del caricamento.
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    companion object {
        const val MAX_RANGE_DAYS = 92
    }
}