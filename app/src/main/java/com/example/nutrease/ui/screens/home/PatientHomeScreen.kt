package com.example.nutrease.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.ui.components.HomeActionCard
import com.example.nutrease.ui.components.specializationLabel
import com.example.nutrease.ui.theme.NutreaseTheme

/**
 * Home del paziente: saluto col nome, card-azione verso diario / discovery / chat
 * (con badge per chat non lette e richieste accettate non viste), campanella per i
 * promemoria. I conteggi si aggiornano al ritorno in home via [LifecycleStartEffect].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    onNavigateToProfile: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToDiary: () -> Unit = {},
    onNavigateToSpecialists: () -> Unit = {},
    onNavigateToReminder: () -> Unit = {},
    onNavigateToChats: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    patientHomeViewModel: PatientHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val badgeState by patientHomeViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.navigateToLogin) {
        if (uiState.navigateToLogin) onNavigateToLogin()
    }

    // Ricalcola i badge (chat non lette + richieste accettate) a ogni ritorno in home:
    // il ViewModel sopravvive alla navigazione, quindi il solo init non basta.
    LifecycleStartEffect(Unit) {
        patientHomeViewModel.refresh()
        onStopOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onNavigateToReminder) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.reminder_title)
                        )
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.AccountCircle, contentDescription = stringResource(R.string.home_profile))
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.home_logout))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = uiState.userName?.let { stringResource(R.string.home_greeting_named, it) }
                    ?: stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.patient_home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (badgeState.isLinkedSpecialistLoaded) {
                LinkedSpecialistCard(
                    specialist = badgeState.linkedSpecialist,
                    onFindSpecialist = onNavigateToSpecialists
                )
            }

            HomeActionCard(
                title = stringResource(R.string.patient_home_diary_title),
                subtitle = stringResource(R.string.patient_home_diary_subtitle),
                icon = Icons.Default.Restaurant,
                onClick = onNavigateToDiary
            )
            HomeActionCard(
                title = stringResource(R.string.patient_home_find_specialist),
                subtitle = stringResource(R.string.patient_home_find_specialist_subtitle),
                icon = Icons.Default.PersonSearch,
                onClick = onNavigateToSpecialists,
                badgeCount = badgeState.acceptedRequestsCount
            )
            HomeActionCard(
                title = stringResource(R.string.patient_home_chat),
                subtitle = stringResource(R.string.patient_home_chat_subtitle),
                icon = Icons.AutoMirrored.Filled.Chat,
                onClick = onNavigateToChats,
                badgeCount = badgeState.unreadChatsCount
            )
        }
    }
}

/**
 * Card di stato del collegamento: con uno specialista attivo ne mostra nome,
 * specializzazione e città; senza, invita alla discovery (tap = naviga). Rende
 * sempre visibile chi è il professionista titolare del fascicolo, dato che una
 * nuova richiesta accettata lo sostituirebbe.
 */
@Composable
private fun LinkedSpecialistCard(
    specialist: Specialist?,
    onFindSpecialist: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (specialist == null) Modifier.clickable(onClick = onFindSpecialist) else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                if (specialist != null) {
                    Text(
                        text = stringResource(R.string.patient_home_linked_specialist_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "${specialist.firstName} ${specialist.lastName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    val details = listOfNotNull(
                        specialist.specialization?.let { specializationLabel(it) },
                        specialist.city?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.patient_home_no_specialist_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.patient_home_no_specialist_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PatientHomePreview() {
    NutreaseTheme { PatientHomeScreen() }
}

@Preview(showBackground = true)
@Composable
private fun LinkedSpecialistCardPreview() {
    NutreaseTheme {
        LinkedSpecialistCard(
            specialist = Specialist(
                taxCode = "ABCDEF80A01H501Z",
                authUid = "uid-1",
                firstName = "Marco",
                lastName = "Bianchi",
                email = "marco@nutrease.it",
                phone = null,
                vatNumber = "01234567890",
                info = null,
                specialization = null,
                city = "Milano"
            ),
            onFindSpecialist = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LinkedSpecialistCardEmptyPreview() {
    NutreaseTheme { LinkedSpecialistCard(specialist = null, onFindSpecialist = {}) }
}