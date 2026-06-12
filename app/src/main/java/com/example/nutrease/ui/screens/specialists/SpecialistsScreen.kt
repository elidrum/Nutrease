package com.example.nutrease.ui.screens.specialists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.SpecializationType
import com.example.nutrease.ui.components.specializationLabel
import com.example.nutrease.ui.theme.NutreaseTheme

/**
 * Schermata "Trova specialista" (RF13–RF14): barra di ricerca + filtri specializzazione
 * e città, lista paginata con infinite-scroll (il trigger scatta qualche card prima del
 * fondo), dialog Material3 per inviare la richiesta con messaggio opzionale.
 */
@Composable
fun SpecialistsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SpecialistsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sentMessage = stringResource(R.string.send_link_request_success)

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.linkRequestSent) {
        if (uiState.linkRequestSent) {
            snackbarHostState.showSnackbar(sentMessage)
            viewModel.consumeLinkRequestSent()
        }
    }

    SpecialistsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onQueryChange = viewModel::updateQuery,
        onSpecializationChange = viewModel::updateSpecialization,
        onCityChange = viewModel::updateCity,
        onLoadMore = viewModel::loadMore,
        onRequestLink = viewModel::startLinkRequest,
        onCancelLinkRequest = viewModel::cancelLinkRequest,
        onConfirmLinkRequest = viewModel::confirmLinkRequest
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpecialistsContent(
    uiState: SpecialistsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSpecializationChange: (SpecializationType?) -> Unit,
    onCityChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRequestLink: (Specialist) -> Unit,
    onCancelLinkRequest: () -> Unit,
    onConfirmLinkRequest: (String?) -> Unit
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val total = listState.layoutInfo.totalItemsCount
            visibleItems.isNotEmpty() && visibleItems.last().index >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.canLoadMore) {
        if (shouldLoadMore && uiState.canLoadMore && !uiState.isLoadingPage && !uiState.isAppending) {
            onLoadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.specialists_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.specialists_search_label)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpecializationFilterDropdown(
                    selected = uiState.specializationFilter,
                    onSelect = onSpecializationChange,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = uiState.cityFilter,
                    onValueChange = onCityChange,
                    label = { Text(stringResource(R.string.specialists_filter_city)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            uiState.linkedSpecialistName?.let { name ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.specialists_already_linked_warning, name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoadingPage && uiState.specialists.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    uiState.specialists.isEmpty() ->
                        Text(
                            text = stringResource(R.string.specialists_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.specialists,
                            key = { it.taxCode }
                        ) { specialist ->
                            SpecialistCard(specialist = specialist, onRequestLink = onRequestLink)
                        }
                        if (uiState.isAppending) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.pendingLinkRequestFor?.let { target ->
        SendLinkRequestDialog(
            specialist = target,
            alreadyLinkedName = uiState.linkedSpecialistName,
            isSending = uiState.isSendingLinkRequest,
            onCancel = onCancelLinkRequest,
            onConfirm = onConfirmLinkRequest
        )
    }
}

// Falso positivo dell'IDE: expanded è riassegnato nelle callback del menu e riletto
// alla ricomposizione (stesso pattern di DiaryScreen).
@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpecializationFilterDropdown(
    selected: SpecializationType?,
    onSelect: (SpecializationType?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.specialists_filter_all)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.let { specializationLabel(it) } ?: allLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.specialists_filter_specialization)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            SpecializationType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(specializationLabel(type)) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SpecialistCard(
    specialist: Specialist,
    onRequestLink: (Specialist) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${specialist.firstName} ${specialist.lastName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            specialist.specialization?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = specializationLabel(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            specialist.city?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            specialist.info?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            specialist.email.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onRequestLink(specialist) }) {
                Icon(
                    Icons.Default.PersonAddAlt1,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.specialists_request_link))
            }
        }
    }
}

@Composable
private fun SendLinkRequestDialog(
    specialist: Specialist,
    alreadyLinkedName: String?,
    isSending: Boolean,
    onCancel: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var message by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isSending) onCancel() },
        title = {
            Text(
                text = stringResource(R.string.send_link_request_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    text = "${specialist.firstName} ${specialist.lastName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                // Avviso al punto d'azione: il banner in lista informa, ma la conferma
                // dell'azione irreversibile passa da qui.
                alreadyLinkedName?.let { name ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.send_link_request_replace_warning, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 500) message = it },
                    label = { Text(stringResource(R.string.send_link_request_message_label)) },
                    supportingText = {
                        Text("${message.length}/500 — ${stringResource(R.string.send_link_request_message_helper)}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending,
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(message) },
                enabled = !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(stringResource(R.string.send_link_request_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isSending) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun SpecialistsPreview() {
    NutreaseTheme {
        SpecialistsContent(
            uiState = SpecialistsUiState(
                specialists = listOf(
                    Specialist(
                        taxCode = "ABCDEF80A01H501Z",
                        authUid = "uid-1",
                        firstName = "Maria",
                        lastName = "Rossi",
                        email = "maria@example.com",
                        phone = null,
                        vatNumber = "01234567890",
                        info = "Specializzata in intolleranze alimentari",
                        specialization = SpecializationType.NUTRIZIONISTA,
                        city = "Roma"
                    )
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onQueryChange = {},
            onSpecializationChange = {},
            onCityChange = {},
            onLoadMore = {},
            onRequestLink = {},
            onCancelLinkRequest = {},
            onConfirmLinkRequest = {}
        )
    }
}