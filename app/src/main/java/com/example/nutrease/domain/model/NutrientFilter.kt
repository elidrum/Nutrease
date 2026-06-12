package com.example.nutrease.domain.model

/**
 * Filtro nutriente nelle viste dello specialista (RF20/RF21). Non filtra la lista:
 * cambia solo cosa viene evidenziato nelle card e aggregato in header/grafico.
 */
enum class NutrientFilter {
    ALL,
    LACTOSE,
    SORBITOL,
    GLUTEN,
    CALORIES
}