package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.ChatDto
import com.example.nutrease.data.dto.MessageDto
import com.example.nutrease.data.dto.UserProfileDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.data.dto.toPreview
import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.domain.model.ChatPreview
import com.example.nutrease.domain.repository.ChatRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlinx.serialization.json.Json

/**
 * Implementazione di [ChatRepository]: letture/scritture via PostgREST, ricezione
 * in tempo reale via Realtime (`postgresChangeFlow` sugli INSERT di `messaggio`,
 * esposto come `Flow` con `channelFlow`). Le RLS valgono anche sul canale Realtime:
 * ognuno riceve solo i messaggi delle proprie chat.
 */
class ChatRepositoryImpl(
    private val supabase: SupabaseClient
) : ChatRepository {

    // Tollerante alle colonne non mappate (Stato, letto_at) presenti nel record
    // Realtime e nelle select complete della tabella messaggio.
    private val json = Json { ignoreUnknownKeys = true }

    // Scope dedicato alla pulizia dei canali Realtime in awaitClose: removeChannel
    // è una suspend fun e va lanciata fuori dal ProducerScope in cancellazione.
    // Il repository è @Singleton, quindi lo scope vive quanto l'app.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getChatsForCurrentUser(): Result<List<ChatPreview>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val currentTaxCode = currentUserTaxCode()
                supabase.from("chat")
                    .select(
                        Columns.raw(
                            """"IdChat","CodFiscalePaziente","CodFiscaleSpecialista",
                               "ultimo_messaggio_at",
                               paziente("Nome","Cognome","CodiceFiscale"),
                               specialista("Nome","Cognome","CodiceFiscale"),
                               messaggio("Testo","created_at")"""
                                .trimIndent()
                        )
                    ) {
                        order("created_at", Order.DESCENDING, referencedTable = "messaggio")
                        limit(1, referencedTable = "messaggio")
                    }
                    .decodeList<ChatDto>()
                    .map { it.toPreview(currentTaxCode) }
                    // Ultimo messaggio più recente in testa; chat senza messaggi in coda.
                    .sortedWith(compareBy(nullsLast(reverseOrder<Instant>())) { it.lastMessageAt })
            }
        }

    override suspend fun getRecentMessages(
        chatId: Long,
        limit: Int
    ): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("messaggio")
                .select {
                    filter { eq("IdChat", chatId) }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<MessageDto>()
                .map { it.toDomain() }
                .reversed() // dal più vecchio al più recente (asc)
        }
    }

    override suspend fun sendMessage(chatId: Long, text: String): Result<ChatMessage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val senderUid = supabase.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("Sessione non disponibile")
                val payload = MessageDto(chatId = chatId, senderUid = senderUid, text = text)
                supabase.from("messaggio")
                    .insert(payload) { select() }
                    .decodeSingle<MessageDto>()
                    .toDomain()
            }
        }

    override fun observeNewMessages(chatId: Long): Flow<ChatMessage> = channelFlow {
        val realtimeChannel = supabase.channel("chat-$chatId")
        val changes = realtimeChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messaggio"
            filter("IdChat", FilterOperator.EQ, chatId)
        }
        changes
            .onEach { action ->
                val dto = json.decodeFromJsonElement(MessageDto.serializer(), action.record)
                trySend(dto.toDomain())
            }
            .launchIn(this)
        realtimeChannel.subscribe()
        awaitClose {
            cleanupScope.launch {
                runCatching { supabase.realtime.removeChannel(realtimeChannel) }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun observeNewMessages(): Flow<Unit> = channelFlow {
        // Nessun filtro per chat: le RLS su `messaggio` (messaggio_select) limitano già la
        // consegna ai messaggi delle chat dell'utente. Ci interessa solo il "qualcosa è
        // arrivato" per far ricalcolare i badge, quindi emettiamo Unit (non il payload).
        val realtimeChannel = supabase.channel("messaggio-all")
        val changes = realtimeChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messaggio"
        }
        changes
            .onEach { trySend(Unit) }
            .launchIn(this)
        realtimeChannel.subscribe()
        awaitClose {
            cleanupScope.launch {
                runCatching { supabase.realtime.removeChannel(realtimeChannel) }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun currentUserTaxCode(): String {
        val uid = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Sessione non disponibile")
        return supabase.from("profilo_utente")
            .select { filter { eq("auth_uid", uid) } }
            .decodeList<UserProfileDto>()
            .firstOrNull()
            ?.codiceFiscale
            ?: throw IllegalStateException("Profilo non trovato")
    }
}