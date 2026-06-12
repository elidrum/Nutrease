package com.example.nutrease.domain.repository

import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.domain.model.ChatPreview
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    /** Le chat dell'utente corrente (RLS-scoped), ordinate per ultimo messaggio. */
    suspend fun getChatsForCurrentUser(): Result<List<ChatPreview>>

    /** Gli ultimi [limit] messaggi della chat, ordinati per `createdAt` crescente. */
    suspend fun getRecentMessages(chatId: Long, limit: Int = 50): Result<List<ChatMessage>>

    /** Invia un messaggio e ritorna la riga creata (id + createdAt server-side). */
    suspend fun sendMessage(chatId: Long, text: String): Result<ChatMessage>

    /** Stream dei nuovi messaggi della chat via Supabase Realtime (INSERT). */
    fun observeNewMessages(chatId: Long): Flow<ChatMessage>

    /**
     * Emette un tick a ogni nuovo messaggio inserito in una qualsiasi chat visibile
     * all'utente corrente (RLS-scoped, senza filtro per chat). Usato dalle home per
     * aggiornare i badge "chat non lette" in tempo reale, senza attendere il ritorno
     * sulla schermata.
     */
    fun observeNewMessages(): Flow<Unit>
}