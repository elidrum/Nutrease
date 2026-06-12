package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Food
import com.example.nutrease.domain.model.FoodSearch
import com.example.nutrease.domain.repository.FoodRepository
import javax.inject.Inject

class SearchFoodsUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    /**
     * Cerca [query] nel dataset alimenti. Il repository serve la lista completa (in cache)
     * e tutto il matching/ranking avviene client-side in [FoodSearch]: tollera typo, regge
     * query multi-parola e ordina per rilevanza. Ritorna al massimo [DISPLAY_LIMIT]
     * risultati; query vuota = nessun risultato.
     */
    suspend operator fun invoke(query: String): Result<List<Food>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        return foodRepository
            .getAllFoods()
            .map { foods -> FoodSearch.rank(trimmed, foods).take(DISPLAY_LIMIT) }
    }

    companion object {
        private const val DISPLAY_LIMIT = 20
    }
}
