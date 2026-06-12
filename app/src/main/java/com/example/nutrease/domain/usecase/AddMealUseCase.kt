package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.repository.DiaryRepository
import javax.inject.Inject

/**
 * Inserimento di un pasto nel diario (RF9). Le invarianti di dominio (almeno un
 * alimento, quantità positive) si controllano qui, prima della rete: valgono per
 * chiunque chiami, non solo per la UI attuale.
 */
class AddMealUseCase @Inject constructor(
    private val diaryRepository: DiaryRepository
) {
    suspend operator fun invoke(meal: Meal): Result<Long> {
        if (meal.items.isEmpty()) {
            return Result.failure(IllegalArgumentException("Aggiungi almeno un alimento al pasto"))
        }
        if (meal.items.any { it.quantityGrams <= 0.0 }) {
            return Result.failure(IllegalArgumentException("Le quantità devono essere maggiori di zero"))
        }
        return diaryRepository.addMeal(meal)
    }
}