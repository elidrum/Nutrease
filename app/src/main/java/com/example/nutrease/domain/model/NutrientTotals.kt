package com.example.nutrease.domain.model

/**
 * Aggregato dei quattro valori monitorati dall'app (grammi di lattosio/sorbitolo/glutine
 * + calorie). L'`operator plus` permette di sommare righe → pasto → giornata con un
 * semplice `fold` partendo da [Zero].
 */
data class NutrientTotals(
    val lactose: Double = 0.0,
    val sorbitol: Double = 0.0,
    val gluten: Double = 0.0,
    val calories: Double = 0.0
) {
    operator fun plus(other: NutrientTotals): NutrientTotals = NutrientTotals(
        lactose = lactose + other.lactose,
        sorbitol = sorbitol + other.sorbitol,
        gluten = gluten + other.gluten,
        calories = calories + other.calories
    )

    companion object {
        val Zero = NutrientTotals()
    }
}