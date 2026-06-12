package com.example.nutrease.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Green700,
    onPrimary = Color.White,
    primaryContainer = Green200,
    onPrimaryContainer = GreenDark,
    secondary = Teal600,
    onSecondary = Color.White,
    secondaryContainer = Teal200,
    background = NeutralGray,
    surface = Color.White,
    error = ErrorRed,
    onError = Color.White
)

/** Tema Material3 dell'app (solo light per l'MVP); avvolge tutte le screen e le Preview. */
@Composable
fun NutreaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}

/**
 * Colori uniformi per la [androidx.compose.material3.TopAppBar] di ogni schermata:
 * stesso sfondo del FAB "+" ([androidx.compose.material3.ColorScheme.primaryContainer],
 * anche da scrollata), così la barra in alto è coordinata col pulsante ovunque.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun nutreaseTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer
)