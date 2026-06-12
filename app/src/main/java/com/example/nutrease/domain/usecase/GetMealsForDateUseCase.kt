package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.repository.DiaryRepository
import kotlinx.datetime.LocalDate
import javax.inject.Inject

/** Pasti di una data per un fascicolo (RF11); riusato anche dalla vista specialista. */
class GetMealsForDateUseCase @Inject constructor(
    private val diaryRepository: DiaryRepository
) {
    suspend operator fun invoke(fascicoloId: Int, date: LocalDate): Result<List<Meal>> =
        diaryRepository.getMealsForDate(fascicoloId, date)
}