package com.example.nutrease.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PersonAddAlt1
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
import com.example.nutrease.ui.components.HomeActionCard
import com.example.nutrease.ui.theme.NutreaseTheme

/**
 * Home dello specialista: card-azione verso richieste (badge pendenti), pazienti e
 * chat (badge non lette); se l'account non è ancora approvato mostra il banner
 * "in verifica". Refresh dei conteggi al ritorno in home via [LifecycleStartEffect].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialistHomeScreen(
    onNavigateToProfile: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToLinkRequests: () -> Unit = {},
    onNavigateToLinkedPatients: () -> Unit = {},
    onNavigateToChats: () -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel(),
    specialistHomeViewModel: SpecialistHomeViewModel = hiltViewModel()
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val specialistState by specialistHomeViewModel.uiState.collectAsStateWithLifecycle()

    // Ricalcola i badge (richieste pendenti + chat non lette) a ogni ritorno in home:
    // il ViewModel sopravvive alla navigazione, quindi il solo init non li aggiornerebbe.
    // Stesso pattern di DiaryScreen.
    LifecycleStartEffect(Unit) {
        specialistHomeViewModel.refresh()
        onStopOrDispose { }
    }

    LaunchedEffect(uiState.navigateToLogin) {
        if (uiState.navigateToLogin) onNavigateToLogin()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.AccountCircle, contentDescription = stringResource(R.string.home_profile))
                    }
                    IconButton(onClick = homeViewModel::logout) {
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
                text = stringResource(R.string.specialist_home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (specialistState.isPendingVerification) {
                PendingVerificationBanner()
            }

            HomeActionCard(
                title = stringResource(R.string.specialist_home_link_requests),
                subtitle = stringResource(R.string.specialist_home_link_requests_subtitle),
                icon = Icons.Default.PersonAddAlt1,
                onClick = onNavigateToLinkRequests,
                badgeCount = specialistState.pendingRequestsCount
            )
            HomeActionCard(
                title = stringResource(R.string.specialist_home_my_patients),
                subtitle = stringResource(R.string.specialist_home_my_patients_subtitle),
                icon = Icons.Default.Groups,
                onClick = onNavigateToLinkedPatients
            )
            HomeActionCard(
                title = stringResource(R.string.specialist_home_chat),
                subtitle = stringResource(R.string.specialist_home_chat_subtitle),
                icon = Icons.AutoMirrored.Filled.Chat,
                onClick = onNavigateToChats,
                badgeCount = specialistState.unreadChatsCount
            )
        }
    }
}

/**
 * Banner mostrato allo specialista registrato ma non ancora approvato da un admin.
 * Spiega perché pazienti/diari/chat risultano vuoti: il suo profilo non è
 * ancora discoverable e non può accettare richieste finché non viene verificato.
 */
@Composable
private fun PendingVerificationBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.specialist_pending_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.specialist_pending_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SpecialistHomePreview() {
    NutreaseTheme { SpecialistHomeScreen() }
}