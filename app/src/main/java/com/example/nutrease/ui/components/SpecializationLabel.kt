package com.example.nutrease.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.nutrease.R
import com.example.nutrease.domain.model.SpecializationType

/**
 * Etichetta italiana della specializzazione, da `strings.xml`. Condivisa tra il form
 * di registrazione e la discovery (stessa label ovunque).
 */
@Composable
fun specializationLabel(type: SpecializationType): String = when (type) {
    SpecializationType.NUTRIZIONISTA -> stringResource(R.string.specialization_nutritionist)
    SpecializationType.DIETISTA -> stringResource(R.string.specialization_dietitian)
    SpecializationType.GASTROENTEROLOGO -> stringResource(R.string.specialization_gastroenterologist)
}