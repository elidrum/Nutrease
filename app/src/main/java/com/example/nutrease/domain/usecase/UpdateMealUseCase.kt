package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.repository.DiaryRepository
import javax.inject.Inject

/**
 * Modifica di un pasto esistente (RF12): aggiorna la testata, poi sostituisce in blocco
 * le righe alimento ([DiaryRepository.replaceMealItems]). `mapCatching` concatena i due
 * passi: se la seconda chiamata fallisce, il Result complessivo è failure.
 */
class UpdateMealUseCase @Inject constructor(
    private val diaryRepository: DiaryRepository
) {
    suspend operator fun invoke(meal: Meal): Result<Unit> {
        val mealId = meal.id ?: return Result.failure(
            IllegalArgumentException("Pasto senza identificatore: impossibile aggiornare")
        )
        if (meal.items.isEmpty()) {
            return Result.failure(IllegalArgumentException("Il pasto deve contenere almeno un alimento"))
        }
        return diaryRepository.updateMeal(meal).mapCatching {
            diaryRepository.replaceMealItems(mealId, meal.items).getOrThrow()
        }
    }
}