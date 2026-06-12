package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.DiaryRepository
import javax.inject.Inject

/** Eliminazione di un pasto (RF12); le righe alimento cadono per ON DELETE CASCADE sul DB. */
class DeleteMealUseCase @Inject constructor(
    private val diaryRepository: DiaryRepository
) {
    suspend operator fun invoke(mealId: Long): Result<Unit> =
        diaryRepository.deleteMeal(mealId)
}