package com.example.nutrease

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.util.Consumer
import androidx.navigation.compose.rememberNavController
import com.example.nutrease.data.notification.ReminderNotificationBuilder
import com.example.nutrease.ui.navigation.AppNavGraph
import com.example.nutrease.ui.theme.NutreaseTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Unica Activity dell'app (single-activity + Navigation Compose): monta il tema e il
 * NavGraph, e gestisce il deep-link della notifica promemoria (extra `nav_target=diary`),
 * sia a freddo sia ad app già aperta.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutreaseTheme {
                val navController = rememberNavController()

                // Intent che guida la navigazione da deep-link: parte da quello di lancio e si
                // aggiorna quando l'activity riceve un nuovo intent da già viva (es. tap sulla
                // notifica promemoria con l'app in foreground/background) — ComponentActivity
                // li consegna a questo listener.
                val currentIntent = remember { mutableStateOf(intent) }
                DisposableEffect(Unit) {
                    val listener = Consumer<Intent> { newIntent -> currentIntent.value = newIntent }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                LaunchedEffect(currentIntent.value) {
                    val navTarget =
                        currentIntent.value.getStringExtra(ReminderNotificationBuilder.EXTRA_NAV_TARGET)
                    if (navTarget == ReminderNotificationBuilder.NAV_TARGET_DIARY) {
                        navController.navigate("diary")
                        // Consuma l'extra per non ri-navigare a ogni ricomposizione o dopo un
                        // cambio di configurazione (setIntent tiene pulito getIntent() al recreate).
                        currentIntent.value.removeExtra(ReminderNotificationBuilder.EXTRA_NAV_TARGET)
                        setIntent(currentIntent.value)
                    }
                }

                AppNavGraph(navController = navController)
            }
        }
    }
}