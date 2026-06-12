package com.example.nutrease.domain.model

import kotlin.time.Instant

/**
 * Riga della lista chat: la controparte (lo specialista per il paziente, il
 * paziente per lo specialista) e l'anteprima dell'ultimo messaggio.
 *
 * `lastMessageAt` è `null` per le chat senza messaggi: in tal caso vanno in coda
 * alla lista ordinata.
 */
data class ChatPreview(
    val chatId: Long,
    val counterpartName: String,
    val counterpartTaxCode: String,
    val lastMessageText: String?,
    val lastMessageAt: Instant?
)