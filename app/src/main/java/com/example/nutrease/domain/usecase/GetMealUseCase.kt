package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.repository.DiaryRepository
import javax.inject.Inject

/** Lettura di un singolo pasto, usata per precompilare il form in modifica (RF12). */
class GetMealUseCase @Inject constructor(
    private val diaryRepository: DiaryRepository
) {
    suspend operator fun invoke(mealId: Long): Result<Meal> =
        diaryRepository.getMeal(mealId)
}