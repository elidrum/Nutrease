package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.model.MealFoodItem
import kotlinx.datetime.LocalDate

/**
 * Contratto del dominio per i pasti del diario (RF9, RF11–RF12). Le letture sono
 * parametriche sul fascicolo, non sull'utente: così le riusa identiche anche lo
 * specialista (le RLS sul DB decidono già chi può leggere cosa). La riga del diario
 * giornaliero non si gestisce qui: la crea un trigger al primo pasto/sintomo del giorno.
 */
interface DiaryRepository {

    /** Id del fascicolo clinico attivo del paziente; errore se non collegato a uno specialista. */
    suspend fun getFascicoloIdForPatient(taxCode: String): Result<Int>

    suspend fun getMealsForDate(fascicoloId: Int, date: LocalDate): Result<List<Meal>>

    suspend fun getMeal(mealId: Long): Result<Meal>

    /** Inserisce pasto + righe alimento; ritorna l'id generato dal DB. */
    suspend fun addMeal(meal: Meal): Result<Long>

    suspend fun updateMeal(meal: Meal): Result<Unit>

    suspend fun deleteMeal(mealId: Long): Result<Unit>

    /** In modifica le righe non si riconciliano una a una: si cancellano e reinseriscono (più semplice e atomico lato client). */
    suspend fun replaceMealItems(mealId: Long, items: List<MealFoodItem>): Result<Unit>
}