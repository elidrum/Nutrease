package com.example.nutrease.domain.model

import kotlin.time.Instant

/**
 * Un singolo messaggio della chat 1-a-1 paziente ↔ specialista.
 *
 * `senderUid` è l'`auth.uid` (uuid string) del mittente: la UI lo confronta con
 * l'uid dell'utente corrente per decidere se la bubble è "mia" o "dell'altro".
 *
 * Lo stato di lettura (`Stato`/`letto_at` sullo schema) è fuori scope MVP e non
 * viene modellato qui: il "letto/non letto" dei badge è solo stato locale di UI.
 */
data class ChatMessage(
    val id: Long,
    val chatId: Long,
    val senderUid: String,
    val text: String,
    val createdAt: Instant
)