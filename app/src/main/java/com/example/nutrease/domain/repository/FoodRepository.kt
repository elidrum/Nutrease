package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.Food

/** Contratto del dominio per il dataset alimentare (RF8). */
interface FoodRepository {
    /**
     * Ritorna l'intero dataset alimenti. L'implementazione lo scarica una volta sola e
     * lo tiene in cache: il dataset è piccolo (~1364 righe) e di fatto immutabile, così
     * la ricerca client-side ([com.example.nutrease.domain.model.FoodSearch]) lavora
     * sulla lista in cache senza altre query.
     */
    suspend fun getAllFoods(): Result<List<Food>>

    suspend fun getFood(id: Int): Result<Food>
}