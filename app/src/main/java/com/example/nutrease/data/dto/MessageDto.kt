package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.ChatMessage
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Riga della tabella `messaggio`. `id` e `createdAt` sono nullabili così che,
 * sull'INSERT, vengano omessi dal payload (il serializer di supabase-kt non
 * serializza i null) e popolati server-side (bigserial + default now()).
 *
 * Le colonne `Stato`/`letto_at` esistono nello schema ma sono fuori scope MVP:
 * non sono mappate qui e vengono ignorate in decodifica (ignoreUnknownKeys).
 */
@Serializable
data class MessageDto(
    @SerialName("IdMessaggio") val id: Long? = null,
    @SerialName("IdChat") val chatId: Long,
    @SerialName("MittenteUid") val senderUid: String,
    @SerialName("Testo") val text: String,
    @SerialName("created_at") val createdAt: String? = null
)

fun MessageDto.toDomain(): ChatMessage = ChatMessage(
    id = id ?: 0L,
    chatId = chatId,
    senderUid = senderUid,
    text = text,
    createdAt = createdAt?.let { Instant.parse(it) } ?: Instant.fromEpochMilliseconds(0)
)