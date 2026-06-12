package com.example.nutrease.domain.repository

import kotlin.time.Instant

/**
 * Stato locale "letto/visto" che alimenta i badge di notifica delle home
 * (chat con messaggi non letti, richieste di collegamento accettate non ancora viste).
 *
 * Persistito lato data layer (così i badge non riappaiono dopo un riavvio); l'interfaccia
 * vive nel dominio e usa solo tipi puri, quindi UseCase/ViewModel restano testabili.
 */
interface BadgeStateRepository {

    /** Timestamp dell'ultimo messaggio "visto" nella chat, oppure null se mai aperta. */
    suspend fun chatLastSeen(chatId: Long): Instant?

    /** Segna come letti i messaggi della chat fino a [lastMessageAt] (non torna indietro). */
    suspend fun setChatLastSeen(chatId: Long, lastMessageAt: Instant)

    /** Id delle richieste accettate già viste dal paziente. */
    suspend fun seenAcceptedRequestIds(): Set<Long>

    /** Aggiunge [ids] all'insieme delle richieste accettate viste. */
    suspend fun markAcceptedRequestsSeen(ids: Set<Long>)
}
