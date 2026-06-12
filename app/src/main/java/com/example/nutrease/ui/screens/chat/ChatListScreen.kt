package com.example.nutrease.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.ChatPreview
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private const val PREVIEW_MAX_CHARS = 50

/**
 * Lista delle chat dell'utente: controparte, anteprima ultimo messaggio e ora
 * relativa; ordinate per ultimo messaggio (chat vuote in coda). Pull-to-refresh.
 */
@Composable
fun ChatListScreen(
    onNavigateBack: () -> Unit,
    onSelectChat: (ChatPreview) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    ChatListContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onSelectChat = onSelectChat
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListContent(
    uiState: ChatListUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectChat: (ChatPreview) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.chat_title)) },
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
                uiState.isLoading && uiState.chats.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                uiState.chats.isEmpty() ->
                    Text(
                        text = stringResource(R.string.chat_list_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = uiState.chats, key = { it.chatId }) { chat ->
                        ChatRow(chat = chat, onClick = { onSelectChat(chat) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatPreview,
    onClick: () -> Unit
) {
    val timeLabel = formatPreviewTimestamp(chat.lastMessageAt)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { CounterpartAvatar(chat.counterpartName) },
        headlineContent = {
            // Nome a sinistra, data ultimo messaggio tutta a destra sulla stessa riga.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.counterpartName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (timeLabel.isNotBlank()) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        supportingContent = chat.lastMessageText?.let { lastText ->
            {
                Text(
                    text = previewText(lastText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

@Composable
private fun CounterpartAvatar(name: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun previewText(lastText: String): String =
    if (lastText.length <= PREVIEW_MAX_CHARS) lastText
    else lastText.take(PREVIEW_MAX_CHARS).trimEnd() + "…"

/** Oggi → HH:mm, ieri → "Ieri", altrimenti → dd/MM. */
@Composable
private fun formatPreviewTimestamp(instant: Instant?): String {
    if (instant == null) return ""
    val tz = TimeZone.currentSystemDefault()
    val dateTime = instant.toLocalDateTime(tz)
    val today = Clock.System.now().toLocalDateTime(tz).date
    val daysAgo = (today.toEpochDays() - dateTime.date.toEpochDays()).toInt()
    return when (daysAgo) {
        0 -> "%02d:%02d".format(dateTime.hour, dateTime.minute)
        1 -> stringResource(R.string.chat_relative_yesterday)
        else -> "%02d/%02d".format(dateTime.date.day, dateTime.date.month.ordinal + 1)
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListPreview() {
    NutreaseTheme {
        ChatListContent(
            uiState = ChatListUiState(
                chats = listOf(
                    ChatPreview(
                        chatId = 1L,
                        counterpartName = "Dott. Mario Rossi",
                        counterpartTaxCode = "RSSMRA80A01H501Z",
                        lastMessageText = "Ci vediamo alla prossima visita, ricordi di compilare il diario.",
                        lastMessageAt = Instant.fromEpochMilliseconds(1_000_000)
                    ),
                    ChatPreview(
                        chatId = 2L,
                        counterpartName = "Anna Bianchi",
                        counterpartTaxCode = "BNCNNA85B41H501Z",
                        lastMessageText = null,
                        lastMessageAt = null
                    )
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onRefresh = {},
            onSelectChat = {}
        )
    }
}