package com.example.nutrease.domain.model

/** Classificazione OMS del BMI (soglie in [computeBmi]). */
enum class BmiCategory { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE }

data class BmiResult(
    val value: Double,
    val category: BmiCategory
)