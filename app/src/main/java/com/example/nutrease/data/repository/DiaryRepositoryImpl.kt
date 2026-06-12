package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.FascicoloIdDto
import com.example.nutrease.data.dto.FoodDto
import com.example.nutrease.data.dto.MealDto
import com.example.nutrease.data.dto.MealFoodDto
import com.example.nutrease.data.dto.toDbValue
import com.example.nutrease.data.dto.toMealType
import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.model.MealFoodItem
import com.example.nutrease.domain.model.NutrientTotals
import com.example.nutrease.domain.repository.DiaryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Implementazione di [DiaryRepository] su PostgREST (tabelle `pasto` + `alimento_pasto`).
 * Due fatti delegati al DB e quindi assenti qui: la riga di `diariogiornaliero` la crea
 * un trigger al primo inserimento del giorno, e i nutrienti per riga li calcola il
 * trigger `calcola_nutrienti_pasto` (il client invia solo grammi + dati originali).
 */
class DiaryRepositoryImpl(
    private val supabase: SupabaseClient
) : DiaryRepository {

    override suspend fun getFascicoloIdForPatient(taxCode: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("fascicoloclinico")
                    .select {
                        filter {
                            eq("CodFiscalePaziente", taxCode)
                            eq("Stato", "Attivo")
                        }
                        limit(1)
                    }
                    .decodeList<FascicoloIdDto>()
                    .firstOrNull()
                    ?.id
                    ?: throw IllegalStateException(
                        "Nessun fascicolo attivo: collegati a uno specialista per iniziare il diario"
                    )
            }
        }

    override suspend fun getMealsForDate(
        fascicoloId: Int,
        date: LocalDate
    ): Result<List<Meal>> = withContext(Dispatchers.IO) {
        runCatching {
            // Tre query piatte (pasti del giorno → righe di quei pasti → alimenti citati)
            // ricomposte in memoria: più semplice da leggere e debuggare di un embed annidato,
            // e il numero di pasti per giorno è piccolo.
            val meals = supabase.from("pasto")
                .select {
                    filter {
                        eq("IdFascicolo", fascicoloId)
                        eq("Data", date.toString())
                    }
                    order("Ora", Order.ASCENDING)
                }
                .decodeList<MealDto>()

            if (meals.isEmpty()) return@runCatching emptyList()

            val mealIds = meals.mapNotNull { it.id }
            val items = supabase.from("alimento_pasto")
                .select {
                    filter { isIn("IdPasto", mealIds) }
                }
                .decodeList<MealFoodDto>()

            val foodIds = items.map { it.foodId }.distinct()
            val foods = if (foodIds.isEmpty()) emptyList() else supabase.from("alimento")
                .select {
                    filter { isIn("IdAlimento", foodIds) }
                }
                .decodeList<FoodDto>()
            val foodById = foods.associateBy { it.id }

            meals.map { mealDto -> mealDto.toDomainMeal(items, foodById) }
        }
    }

    override suspend fun getMeal(mealId: Long): Result<Meal> = withContext(Dispatchers.IO) {
        runCatching {
            val mealDto = supabase.from("pasto")
                .select { filter { eq("IdPasto", mealId) } }
                .decodeSingle<MealDto>()

            val items = supabase.from("alimento_pasto")
                .select { filter { eq("IdPasto", mealId) } }
                .decodeList<MealFoodDto>()

            val foodIds = items.map { it.foodId }.distinct()
            val foods = if (foodIds.isEmpty()) emptyList() else supabase.from("alimento")
                .select { filter { isIn("IdAlimento", foodIds) } }
                .decodeList<FoodDto>()
            val foodById = foods.associateBy { it.id }

            mealDto.toDomainMeal(items, foodById)
        }
    }

    override suspend fun addMeal(meal: Meal): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val mealDto = MealDto(
                fascicoloId = meal.fascicoloId,
                date = meal.date.toString(),
                time = meal.time.toDbString(),
                type = meal.type.toDbValue(),
                description = meal.description
            )
            val inserted = supabase.from("pasto")
                .insert(mealDto) { select() }
                .decodeSingle<MealDto>()
            val mealId = inserted.id
                ?: throw IllegalStateException("Inserimento pasto: ID non restituito")

            val itemDtos = meal.items.map { it.toDto(mealId) }
            if (itemDtos.isNotEmpty()) {
                supabase.from("alimento_pasto").insert(itemDtos)
            }
            mealId
        }
    }

    override suspend fun updateMeal(meal: Meal): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mealId = meal.id
                ?: throw IllegalArgumentException("Pasto senza ID: impossibile aggiornare")
            supabase.from("pasto").update({
                set("Data", meal.date.toString())
                set("Ora", meal.time.toDbString())
                set("Tipologia", meal.type.toDbValue())
                set("Descrizione", meal.description)
            }) {
                filter { eq("IdPasto", mealId) }
            }
            Unit
        }
    }

    override suspend fun deleteMeal(mealId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("pasto")
                    .delete { filter { eq("IdPasto", mealId) } }
                Unit
            }
        }

    override suspend fun replaceMealItems(
        mealId: Long,
        items: List<MealFoodItem>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("alimento_pasto")
                .delete { filter { eq("IdPasto", mealId) } }
            val dtos = items.map { it.toDto(mealId) }
            if (dtos.isNotEmpty()) {
                supabase.from("alimento_pasto").insert(dtos)
            }
            Unit
        }
    }
}

/** Formato `HH:mm:ss` atteso dalla colonna `time` di Postgres. */
private fun LocalTime.toDbString(): String {
    val h = hour.toString().padStart(2, '0')
    val m = minute.toString().padStart(2, '0')
    val s = second.toString().padStart(2, '0')
    return "$h:$m:$s"
}

/** Postgres può restituire l'ora con i microsecondi ("13:00:00.123456"): li scartiamo prima del parse. */
private fun parseDbTime(raw: String): LocalTime {
    val withoutMicros = raw.substringBefore('.')
    return LocalTime.parse(withoutMicros)
}

private fun MealDto.toDomainMeal(
    allItems: List<MealFoodDto>,
    foodById: Map<Int, FoodDto>
): Meal {
    val mealId = id
    val itemsForMeal = allItems
        .filter { it.mealId == mealId }
        .map { it.toDomain(foodById[it.foodId]) }
    return Meal(
        id = mealId,
        fascicoloId = fascicoloId,
        date = LocalDate.parse(date),
        time = parseDbTime(time),
        type = type.toMealType(),
        description = description,
        items = itemsForMeal
    )
}

private fun MealFoodDto.toDomain(food: FoodDto?): MealFoodItem = MealFoodItem(
    id = id,
    foodId = foodId,
    foodName = food?.name ?: "Alimento sconosciuto",
    quantityGrams = quantityGrams,
    originalUnit = originalUnit ?: "g",
    originalQuantity = originalQuantity ?: quantityGrams,
    nutrients = NutrientTotals(
        lactose = lactose,
        sorbitol = sorbitol,
        gluten = gluten,
        calories = calories
    )
)

private fun MealFoodItem.toDto(mealId: Long): MealFoodDto = MealFoodDto(
    mealId = mealId,
    foodId = foodId,
    quantityGrams = quantityGrams,
    originalUnit = originalUnit,
    originalQuantity = originalQuantity
)