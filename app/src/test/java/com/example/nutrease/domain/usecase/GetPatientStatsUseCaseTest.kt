package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.DiaryDateRange
import com.example.nutrease.domain.model.NutrientFilter
import com.example.nutrease.domain.model.NutrientTotals
import com.example.nutrease.domain.model.PatientDiaryDay
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.model.SymptomType
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetPatientStatsUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val rangeUseCase: GetPatientDiaryRangeUseCase = mockk()

    private val specialist = AuthUser(
        id = "auth-1",
        email = "s@example.com",
        role = UserRole.SPECIALIST,
        taxCode = "S1"
    )

    private val today = LocalDate(2026, 5, 27)

    private fun useCase() = GetPatientStatsUseCase(
        authRepository = authRepository,
        getPatientDiaryRangeUseCase = rangeUseCase
    )

    private fun symptom(date: LocalDate, type: SymptomType): Symptom = Symptom(
        id = null,
        fascicoloId = 1,
        date = date,
        time = LocalTime(12, 0),
        type = type,
        severity = SymptomSeverity.MILD
    )

    private fun day(
        date: LocalDate,
        lactose: Double = 0.0,
        sorbitol: Double = 0.0,
        gluten: Double = 0.0,
        calories: Double = 0.0,
        symptoms: List<Symptom> = emptyList()
    ): PatientDiaryDay = PatientDiaryDay(
        date = date,
        meals = emptyList(),
        symptoms = symptoms,
        totals = NutrientTotals(lactose, sorbitol, gluten, calories)
    )

    @Test
    fun `non specialist is rejected`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist.copy(role = UserRole.PATIENT)

        val result = useCase().invoke(fascicoloId = 1, today = today)

        assertTrue(result.isFailure)
    }

    @Test
    fun `daily series contains 30 ascending points for each nutrient key`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist
        val days = (0 until 30).map { offset ->
            day(today.minus(offset, DateTimeUnit.DAY), lactose = offset.toDouble())
        }
        coEvery {
            rangeUseCase(fascicoloId = 1, range = DiaryDateRange.Last30Days, today = today)
        } returns Result.success(days)

        val stats = useCase().invoke(fascicoloId = 1, today = today).getOrThrow()

        listOf(
            NutrientFilter.LACTOSE,
            NutrientFilter.SORBITOL,
            NutrientFilter.GLUTEN,
            NutrientFilter.CALORIES
        ).forEach { filter ->
            val series = stats.dailySeriesByFilter[filter]
            assertEquals("series for $filter", 30, series?.size)
            val dates = series!!.map { it.date }
            assertEquals(dates.sorted(), dates) // ASC by date
        }
        // ALL must not be present in the map
        assertTrue(stats.dailySeriesByFilter[NutrientFilter.ALL] == null)
    }

    @Test
    fun `top symptoms ordered by count desc with stable tie-break by ordinal`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist
        val day0 = today.minus(0, DateTimeUnit.DAY)
        val day1 = today.minus(1, DateTimeUnit.DAY)
        val days = listOf(
            day(day0, symptoms = listOf(
                symptom(day0, SymptomType.BLOATING),
                symptom(day0, SymptomType.BLOATING),
                symptom(day0, SymptomType.BLOATING),
                symptom(day0, SymptomType.CRAMPS),
                symptom(day0, SymptomType.CRAMPS),
                symptom(day0, SymptomType.NAUSEA),
                symptom(day0, SymptomType.DIARRHEA)
            )),
            day(day1)
        )
        coEvery {
            rangeUseCase(fascicoloId = 1, range = DiaryDateRange.Last30Days, today = today)
        } returns Result.success(days)

        val stats = useCase().invoke(fascicoloId = 1, today = today).getOrThrow()

        assertEquals(3, stats.topSymptoms.size)
        assertEquals(SymptomType.BLOATING, stats.topSymptoms[0].type)
        assertEquals(3, stats.topSymptoms[0].count)
        assertEquals(SymptomType.CRAMPS, stats.topSymptoms[1].type)
        assertEquals(2, stats.topSymptoms[1].count)
        // Tie between DIARRHEA(ordinal 2) and NAUSEA(ordinal 4): DIARRHEA wins
        assertEquals(SymptomType.DIARRHEA, stats.topSymptoms[2].type)
        assertEquals(1, stats.topSymptoms[2].count)
    }

    @Test
    fun `symptomatic days percent is days with symptoms over total`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist
        val days = (0 until 30).map { offset ->
            val date = today.minus(offset, DateTimeUnit.DAY)
            val symptoms = if (offset < 6) listOf(symptom(date, SymptomType.NAUSEA)) else emptyList()
            day(date, symptoms = symptoms)
        }
        coEvery {
            rangeUseCase(fascicoloId = 1, range = DiaryDateRange.Last30Days, today = today)
        } returns Result.success(days)

        val stats = useCase().invoke(fascicoloId = 1, today = today).getOrThrow()

        assertEquals(20.0, stats.symptomaticDaysPercent, 0.0001)
        assertEquals(30, stats.totalDays)
    }

    @Test
    fun `empty diary returns empty stats with bmi null`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist
        coEvery {
            rangeUseCase(fascicoloId = 1, range = DiaryDateRange.Last30Days, today = today)
        } returns Result.success(emptyList())

        val stats = useCase().invoke(fascicoloId = 1, today = today).getOrThrow()

        assertTrue(stats.bmi == null)
        assertTrue(stats.topSymptoms.isEmpty())
        assertEquals(0.0, stats.symptomaticDaysPercent, 0.0001)
        assertEquals(0, stats.totalDays)
        assertTrue(stats.dailySeriesByFilter[NutrientFilter.LACTOSE]?.isEmpty() ?: false)
    }
}
