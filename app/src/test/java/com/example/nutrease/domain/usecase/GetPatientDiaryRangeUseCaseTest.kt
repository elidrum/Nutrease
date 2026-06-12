package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.DiaryDateRange
import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.model.MealFoodItem
import com.example.nutrease.domain.model.MealType
import com.example.nutrease.domain.model.NutrientTotals
import com.example.nutrease.domain.model.Symptom
import com.example.nutrease.domain.model.SymptomSeverity
import com.example.nutrease.domain.model.SymptomType
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.DiaryRepository
import com.example.nutrease.domain.repository.SymptomRepository
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

class GetPatientDiaryRangeUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val diaryRepository: DiaryRepository = mockk()
    private val symptomRepository: SymptomRepository = mockk()

    private val specialist = AuthUser(
        id = "auth-1",
        email = "s@example.com",
        role = UserRole.SPECIALIST,
        taxCode = "S1"
    )

    private fun useCase() = GetPatientDiaryRangeUseCase(
        authRepository = authRepository,
        diaryRepository = diaryRepository,
        symptomRepository = symptomRepository
    )

    private fun meal(id: Long, date: LocalDate, lactose: Double): Meal = Meal(
        id = id,
        fascicoloId = 1,
        date = date,
        time = LocalTime(12, 0),
        type = MealType.LUNCH,
        description = null,
        items = listOf(
            MealFoodItem(
                id = id,
                foodId = 1,
                foodName = "Test",
                quantityGrams = 100.0,
                originalUnit = "g",
                originalQuantity = 100.0,
                nutrients = NutrientTotals(
                    lactose = lactose,
                    sorbitol = 0.0,
                    gluten = 0.0,
                    calories = 200.0
                )
            )
        )
    )

    private fun symptom(date: LocalDate): Symptom = Symptom(
        id = 10L,
        fascicoloId = 1,
        date = date,
        time = LocalTime(15, 0),
        type = SymptomType.BLOATING,
        severity = SymptomSeverity.MILD
    )

    @Test
    fun `range over the limit returns failure`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist
        val today = LocalDate(2026, 5, 27)
        val tooFar = today.minusDays(100)

        val result = useCase().invoke(
            fascicoloId = 1,
            range = DiaryDateRange.Custom(tooFar, today),
            today = today
        )

        assertTrue(result.isFailure)
        assertEquals(
            IllegalArgumentException::class,
            result.exceptionOrNull()!!::class
        )
    }

    @Test
    fun `non specialist user is rejected`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist.copy(role = UserRole.PATIENT)

        val today = LocalDate(2026, 5, 27)
        val result = useCase().invoke(
            fascicoloId = 1,
            range = DiaryDateRange.Today,
            today = today
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `totals are aggregated per day and ordered descending`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist

        val today = LocalDate(2026, 5, 27)
        val yesterday = today.minusDays(1)

        coEvery { diaryRepository.getMealsForDate(1, today) } returns
            Result.success(listOf(meal(1, today, lactose = 5.0), meal(2, today, lactose = 2.5)))
        coEvery { diaryRepository.getMealsForDate(1, yesterday) } returns
            Result.success(listOf(meal(3, yesterday, lactose = 1.0)))
        coEvery { symptomRepository.getSymptomsForDate(1, today) } returns
            Result.success(listOf(symptom(today)))
        coEvery { symptomRepository.getSymptomsForDate(1, yesterday) } returns
            Result.success(emptyList())

        val result = useCase().invoke(
            fascicoloId = 1,
            range = DiaryDateRange.Custom(yesterday, today),
            today = today
        )

        assertTrue(result.isSuccess)
        val days = result.getOrThrow()
        assertEquals(2, days.size)
        assertEquals(today, days[0].date)
        assertEquals(yesterday, days[1].date)
        assertEquals(7.5, days[0].totals.lactose, 0.0001)
        assertEquals(1.0, days[1].totals.lactose, 0.0001)
        assertEquals(1, days[0].symptoms.size)
        assertEquals(0, days[1].symptoms.size)
    }

    @Test
    fun `today range produces exactly one day`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialist
        val today = LocalDate(2026, 5, 27)
        coEvery { diaryRepository.getMealsForDate(1, today) } returns Result.success(emptyList())
        coEvery { symptomRepository.getSymptomsForDate(1, today) } returns Result.success(emptyList())

        val result = useCase().invoke(
            fascicoloId = 1,
            range = DiaryDateRange.Today,
            today = today
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
    }

    private fun LocalDate.minusDays(days: Int): LocalDate = this.minus(days, DateTimeUnit.DAY)
}