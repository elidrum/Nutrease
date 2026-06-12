package com.example.nutrease.ui.screens.linkedpatients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.LinkedPatient
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime

/**
 * Lista "I miei pazienti" dello specialista (RF18): card per paziente, tap → diario
 * read-only di quel fascicolo (RF19).
 */
@Composable
fun LinkedPatientsScreen(
    onNavigateBack: () -> Unit,
    onSelectPatient: (LinkedPatient) -> Unit,
    viewModel: LinkedPatientsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LinkedPatientsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onSelectPatient = onSelectPatient
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkedPatientsContent(
    uiState: LinkedPatientsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectPatient: (LinkedPatient) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.linked_patients_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.patients.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                uiState.patients.isEmpty() ->
                    Text(
                        text = stringResource(R.string.linked_patients_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = uiState.patients, key = { it.taxCode }) { patient ->
                        LinkedPatientCard(
                            patient = patient,
                            onClick = { onSelectPatient(patient) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedPatientCard(
    patient: LinkedPatient,
    onClick: () -> Unit
) {
    val today = remember { todayLocalDate() }
    val ageText = patient.birthDate?.let { bd ->
        val age = computeAge(bd, today)
        if (age >= 0) pluralStringResource(R.plurals.linked_patients_age, age, age) else null
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${patient.firstName} ${patient.lastName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ageText?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            patient.email?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun todayLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun computeAge(birthDate: LocalDate, today: LocalDate): Int {
    var years = today.year - birthDate.year
    val hadBirthdayThisYear = (today.month > birthDate.month) ||
        (today.month == birthDate.month && today.day >= birthDate.day)
    if (!hadBirthdayThisYear) years--
    return years
}

@Preview(showBackground = true)
@Composable
private fun LinkedPatientsPreview() {
    NutreaseTheme {
        LinkedPatientsContent(
            uiState = LinkedPatientsUiState(
                patients = listOf(
                    LinkedPatient(
                        taxCode = "RSSMRA80A01H501Z",
                        fascicoloId = 1,
                        firstName = "Mario",
                        lastName = "Rossi",
                        email = "mario.rossi@example.com",
                        birthDate = LocalDate(1980, 1, 1)
                    )
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onRefresh = {},
            onSelectPatient = {}
        )
    }
}