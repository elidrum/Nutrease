package com.example.nutrease.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.ui.components.MessageBubble
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private const val NEAR_BOTTOM_THRESHOLD = 3

/**
 * Conversazione 1-a-1 (RF23–RF24): bubble stile messaging (proprie a destra), campo
 * di invio max 4 righe, ricezione live via Realtime. All'arrivo di un messaggio:
 * auto-scroll se si è vicini al fondo, altrimenti banner "Nuovi messaggi ↓".
 */
// Falso positivo dell'IDE: 'showNewMessagesBanner' è riassegnato nelle LaunchedEffect / callback
// di scroll e riletto alla ricomposizione, non visibile all'analisi statica.
@Suppress("AssignedValueIsNeverRead")
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Raggruppa i messaggi per giorno: tra una giornata e l'altra si inserisce un separatore
    // di data (stile WhatsApp), così la timeline indica sempre il giorno dei messaggi.
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val rows = remember(uiState.messages) { buildChatRows(uiState.messages, timeZone) }

    val sendErrorText = stringResource(R.string.chat_send_error)
    LaunchedEffect(uiState.sendFailed) {
        if (uiState.sendFailed) {
            snackbarHostState.showSnackbar(sendErrorText)
            viewModel.consumeSendError()
        }
    }

    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total == 0 || lastVisible >= total - 1 - NEAR_BOTTOM_THRESHOLD
        }
    }

    var showNewMessagesBanner by remember { mutableStateOf(false) }
    // Al primo caricamento la chat deve aprirsi in fondo (ultimi messaggi). Il salto iniziale
    // è istantaneo (scrollToItem, non animato): l'auto-scroll animato basato su isNearBottom
    // legge un layoutInfo ancora vuoto al primo frame e non posiziona affidabilmente in fondo.
    var hasInitialScroll by rememberSaveable { mutableStateOf(false) }

    // Dopo il primo salto: auto-scroll all'ultima riga se l'utente è già vicino al fondo,
    // altrimenti mostra il banner "Nuovi messaggi". Si usa rows.lastIndex (non messages)
    // perché la lista include anche i separatori di data.
    LaunchedEffect(rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        if (!hasInitialScroll) {
            listState.scrollToItem(rows.lastIndex)
            hasInitialScroll = true
        } else if (isNearBottom) {
            listState.animateScrollToItem(rows.lastIndex)
        } else {
            showNewMessagesBanner = true
        }
    }
    LaunchedEffect(isNearBottom) {
        if (isNearBottom) showNewMessagesBanner = false
    }

    ChatContent(
        uiState = uiState,
        rows = rows,
        listState = listState,
        snackbarHostState = snackbarHostState,
        showNewMessagesBanner = showNewMessagesBanner,
        onNavigateBack = onNavigateBack,
        onSend = viewModel::sendMessage,
        onScrollToBottom = {
            coroutineScope.launch {
                if (rows.isNotEmpty()) {
                    listState.animateScrollToItem(rows.lastIndex)
                }
                showNewMessagesBanner = false
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    uiState: ChatUiState,
    rows: List<ChatRow>,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    showNewMessagesBanner: Boolean,
    onNavigateBack: () -> Unit,
    onSend: (String) -> Unit,
    onScrollToBottom: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(uiState.counterpartName) },
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
        bottomBar = {
            ChatInputBar(isSending = uiState.isSending, onSend = onSend)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.messages.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                uiState.error != null && uiState.messages.isEmpty() ->
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = rows,
                        key = { row ->
                            when (row) {
                                is ChatRow.Day -> "day-${row.date}"
                                is ChatRow.Message -> row.message.id
                            }
                        }
                    ) { row ->
                        when (row) {
                            is ChatRow.Day -> DateSeparator(row.date)
                            is ChatRow.Message -> MessageBubble(
                                message = row.message,
                                isMine = row.message.senderUid == uiState.currentUserUid
                            )
                        }
                    }
                }
            }

            if (showNewMessagesBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.large,
                    onClick = onScrollToBottom,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.chat_new_messages),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    isSending: Boolean,
    onSend: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank() && !isSending

    Surface(tonalElevation = 3.dp) {
        // Insets della barra di input: union() prende il MASSIMO tra nav bar e IME, non la
        // somma. A tastiera chiusa il padding è quello della nav bar (il campo resta subito
        // sopra i tasti indietro/home/recenti, non dietro di essi); a tastiera aperta è quello
        // dell'IME (che già copre la nav bar), senza vuoto né doppio padding. Applicato alla Row
        // interna così lo sfondo tonale del Surface riempie comunque dietro la nav bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val toSend = text
                    text = ""
                    onSend(toSend)
                },
                enabled = canSend
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send_action)
                )
            }
        }
    }
}

/**
 * Riga della timeline chat: o un separatore di giornata, o un messaggio. Permette di
 * inserire gli header di data tra i messaggi mantenendo un solo indice per la LazyColumn.
 */
private sealed interface ChatRow {
    data class Day(val date: LocalDate) : ChatRow
    data class Message(val message: ChatMessage) : ChatRow
}

/** Inserisce un separatore [ChatRow.Day] prima del primo messaggio di ogni nuova giornata. */
private fun buildChatRows(messages: List<ChatMessage>, timeZone: TimeZone): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var lastDate: LocalDate? = null
    messages.forEach { message ->
        val date = message.createdAt.toLocalDateTime(timeZone).date
        if (date != lastDate) {
            rows += ChatRow.Day(date)
            lastDate = date
        }
        rows += ChatRow.Message(message)
    }
    return rows
}

/** Pillola centrata con la giornata ("Oggi" / "Ieri" / "9 giugno 2026"). */
@Composable
private fun DateSeparator(date: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = dayLabel(date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return when (date) {
        today -> "Oggi"
        today.minus(DatePeriod(days = 1)) -> "Ieri"
        else -> "${date.day} ${italianMonth(date.month)} ${date.year}"
    }
}

private fun italianMonth(month: Month): String = when (month) {
    Month.JANUARY -> "gennaio"
    Month.FEBRUARY -> "febbraio"
    Month.MARCH -> "marzo"
    Month.APRIL -> "aprile"
    Month.MAY -> "maggio"
    Month.JUNE -> "giugno"
    Month.JULY -> "luglio"
    Month.AUGUST -> "agosto"
    Month.SEPTEMBER -> "settembre"
    Month.OCTOBER -> "ottobre"
    Month.NOVEMBER -> "novembre"
    Month.DECEMBER -> "dicembre"
}

private fun sampleChatUiState() = ChatUiState(
    counterpartName = "Mario Rossi",
    currentUserUid = "uid-me",
    isLoading = false,
    messages = listOf(
        ChatMessage(1L, 1L, "uid-other", "Buongiorno, come va?", Instant.fromEpochMilliseconds(1_000)),
        ChatMessage(2L, 1L, "uid-me", "Tutto bene, grazie!", Instant.fromEpochMilliseconds(2_000))
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContentPreviewBody() {
    val sample = sampleChatUiState()
    NutreaseTheme {
        ChatContent(
            uiState = sample,
            rows = buildChatRows(sample.messages, TimeZone.currentSystemDefault()),
            listState = rememberLazyListState(),
            snackbarHostState = remember { SnackbarHostState() },
            showNewMessagesBanner = false,
            onNavigateBack = {},
            onSend = {},
            onScrollToBottom = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatPreview() {
    ChatContentPreviewBody()
}

/** Verifica accessibilità (Fase 3): la barra di input regge a font-scale 200%. */
@Preview(showBackground = true, fontScale = 2f, heightDp = 640)
@Composable
private fun ChatLargeFontPreview() {
    ChatContentPreviewBody()
}