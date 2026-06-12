package com.example.nutrease.domain.model

/**
 * Indice di massa corporea (peso/altezza²) con classificazione OMS: sottopeso <18.5,
 * normopeso <25, sovrappeso <30, obeso oltre. Null su input non positivi. Pronto per
 * quando entreranno peso/altezza (modulo misurazioni, post-MVP).
 */
fun computeBmi(weightKg: Double, heightMeters: Double): BmiResult? {
    if (weightKg <= 0.0 || heightMeters <= 0.0) return null
    val value = weightKg / (heightMeters * heightMeters)
    val category = when {
        value < 18.5 -> BmiCategory.UNDERWEIGHT
        value < 25.0 -> BmiCategory.NORMAL
        value < 30.0 -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }
    return BmiResult(value = value, category = category)
}