package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Food
import com.example.nutrease.domain.repository.FoodRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The use case is thin: it delegates matching/ranking to [com.example.nutrease.domain.model.FoodSearch]
 * (tested in FoodSearchTest) and only owns the empty-query short-circuit and the display cap.
 */
class SearchFoodsUseCaseTest {

    private val repository: FoodRepository = mockk()
    private val useCase = SearchFoodsUseCase(repository)

    @Test
    fun `empty or whitespace query returns empty list without hitting the repository`() = runTest {
        assertEquals(emptyList<Food>(), useCase("").getOrThrow())
        assertEquals(emptyList<Food>(), useCase("   ").getOrThrow())
        coVerify(exactly = 0) { repository.getAllFoods() }
    }

    @Test
    fun `delegates to FoodSearch ranking over the full dataset`() = runTest {
        val dataset = listOf(
            food("Soppressata"),                 // no "pasta" anywhere -> filtered out
            food("Insalata di pasta"),           // contains "pasta" as a word
            food("Pasta alla carbonara"),        // name starts with the query
            food("Pasta"),                       // exact name == query
            food("Pasta al ragù alla bolognese") // name starts with the query, longer
        )
        coEvery { repository.getAllFoods() } returns Result.success(dataset)

        val ranked = useCase("pasta").getOrThrow().map { it.name }

        assertEquals(
            listOf(
                "Pasta",                         // exact name
                "Pasta alla carbonara",          // prefix bonus, shorter
                "Pasta al ragù alla bolognese",  // prefix bonus, longer
                "Insalata di pasta"              // word match, no prefix bonus
            ),
            ranked
        )
    }

    @Test
    fun `result truncated to display limit`() = runTest {
        val many = (1..30).map { food("Pasta variante $it") }
        coEvery { repository.getAllFoods() } returns Result.success(many)

        assertEquals(20, useCase("pasta").getOrThrow().size)
    }

    @Test
    fun `repository failure is propagated`() = runTest {
        val boom = RuntimeException("network down")
        coEvery { repository.getAllFoods() } returns Result.failure(boom)

        assertEquals(boom, useCase("pasta").exceptionOrNull())
    }

    private fun food(name: String): Food = Food(
        id = name.hashCode(),
        name = name,
        category = null,
        lactosePer100g = 0.0,
        sorbitolPer100g = 0.0,
        glutenPer100g = 0.0,
        caloriesPer100g = 0.0,
        unitConversions = emptyMap()
    )
}
