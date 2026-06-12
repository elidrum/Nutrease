package com.example.nutrease.ui.screens.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.ui.screens.splash.RootViewModel.StartState
import com.example.nutrease.ui.theme.NutreaseTheme

/**
 * Schermata di avvio: mostra logo + spinner mentre [RootViewModel] risolve la sessione
 * persistita, poi naviga (via [LaunchedEffect], mai durante la composizione) al login
 * o alla home per ruolo. Come tutte le screen, riceve callback `onNavigate*` dall'alto:
 * la navigazione resta centralizzata nel NavGraph.
 */
@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToPatientHome: () -> Unit,
    onNavigateToSpecialistHome: () -> Unit,
    viewModel: RootViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (state) {
            StartState.Loading -> Unit
            StartState.Login -> onNavigateToLogin()
            StartState.PatientHome -> onNavigateToPatientHome()
            StartState.SpecialistHome -> onNavigateToSpecialistHome()
        }
    }

    SplashContent()
}

@Composable
private fun SplashContent() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 24.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashPreview() {
    NutreaseTheme { SplashContent() }
}