package com.example.nutrease.ui.screens.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.LinkRequestWithPatient
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Inbox richieste dello specialista (RF15–RF17): card con nome paziente, data e
 * messaggio, azioni Accetta/Rifiuta. Il rifiuto apre una dialog con motivazione
 * obbligatoria (conferma disabilitata finché il campo è vuoto).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkRequestsScreen(
    onNavigateBack: () -> Unit,
    viewModel: LinkRequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val acceptedMessage = stringResource(R.string.link_requests_accepted)
    val rejectedMessage = stringResource(R.string.link_requests_rejected)

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            val text = when (it) {
                LinkRequestsUiState.ToastMessage.ACCEPTED -> acceptedMessage
                LinkRequestsUiState.ToastMessage.REJECTED -> rejectedMessage
            }
            snackbarHostState.showSnackbar(text)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.link_requests_title)) },
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
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.requests.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                uiState.requests.isEmpty() ->
                    Text(
                        text = stringResource(R.string.link_requests_empty),
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
                    items(
                        items = uiState.requests,
                        key = { it.request.id }
                    ) { item ->
                        LinkRequestCard(
                            item = item,
                            isProcessing = uiState.processingId == item.request.id,
                            onAccept = { viewModel.accept(item) },
                            onReject = { viewModel.startReject(item) }
                        )
                    }
                }
            }
        }
    }

    uiState.rejectingRequest?.let { target ->
        RejectDialog(
            target = target,
            onCancel = viewModel::cancelReject,
            onConfirm = viewModel::confirmReject
        )
    }
}

@Composable
private fun LinkRequestCard(
    item: LinkRequestWithPatient,
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${item.patientFirstName} ${item.patientLastName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatRequestDate(item.request.requestedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            item.request.message?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(stringResource(R.string.link_requests_accept))
                }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.link_requests_reject))
                }
            }
        }
    }
}

@Composable
private fun RejectDialog(
    target: LinkRequestWithPatient,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.link_requests_reject_dialog_title)) },
        text = {
            Column {
                Text(
                    text = "${target.patientFirstName} ${target.patientLastName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    label = { Text(stringResource(R.string.link_requests_reject_reason_label)) },
                    supportingText = {
                        Text("${reason.length}/500 — ${stringResource(R.string.link_requests_reject_reason_helper)}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    isError = reason.isBlank()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank()
            ) {
                Text(stringResource(R.string.link_requests_reject_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private fun formatRequestDate(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dt.day.toString().padStart(2, '0')
    val month = (dt.month.ordinal + 1).toString().padStart(2, '0')
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "$day/$month/${dt.year} $hour:$minute"
}

@Preview(showBackground = true)
@Composable
private fun LinkRequestsPreview() {
    NutreaseTheme {
        LinkRequestCard(
            item = LinkRequestWithPatient(
                request = LinkRequest(
                    id = 1L,
                    patientTaxCode = "RSSMRA80A01H501Z",
                    specialistTaxCode = "LSEMRC80A01H501U",
                    status = LinkRequestStatus.PENDING,
                    message = "Vorrei iniziare un percorso nutrizionale",
                    requestedAt = Instant.parse("2026-05-01T09:30:00Z"),
                    respondedAt = null,
                    rejectionReason = null
                ),
                patientFirstName = "Mario",
                patientLastName = "Rossi"
            ),
            isProcessing = false,
            onAccept = {},
            onReject = {}
        )
    }
}