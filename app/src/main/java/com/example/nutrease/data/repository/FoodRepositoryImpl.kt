package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.FoodDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.domain.model.Food
import com.example.nutrease.domain.repository.FoodRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Implementazione di [FoodRepository]: scarica l'intera tabella `alimento` UNA volta
 * e la serve dalla cache in memoria. È bindato `@Singleton` nel modulo Hilt, quindi
 * la cache è condivisa da tutta l'app.
 */
class FoodRepositoryImpl(
    private val supabase: SupabaseClient
) : FoodRepository {

    // Cache in memoria dell'intero dataset. La lista è piccola (~1364 righe) e di fatto
    // immutabile: si scarica una volta e si riusa per ogni ricerca.
    // Il Mutex evita che due ricerche concorrenti lancino due fetch paralleli.
    private val cacheLock = Mutex()
    @Volatile
    private var cachedFoods: List<Food>? = null

    override suspend fun getAllFoods(): Result<List<Food>> = withContext(Dispatchers.IO) {
        // Percorso veloce: già in cache, niente lock, niente rete.
        cachedFoods?.let { return@withContext Result.success(it) }

        cacheLock.withLock {
            // Ricontrollo dentro il lock: un'altra coroutine può aver riempito la cache
            // mentre eravamo in attesa.
            cachedFoods?.let { return@withContext Result.success(it) }

            runCatching {
                supabase.from("alimento")
                    .select { order("Nome", Order.ASCENDING) }
                    .decodeList<FoodDto>()
                    .map { it.toDomain() }
            }.onSuccess { cachedFoods = it } // si cacha solo il fetch riuscito; gli errori si ritentano
        }
    }

    override suspend fun getFood(id: Int): Result<Food> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("alimento")
                .select { filter { eq("IdAlimento", id) } }
                .decodeSingle<FoodDto>()
                .toDomain()
        }
    }
}