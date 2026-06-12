package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.repository.BadgeStateRepository
import com.example.nutrease.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * Numero di chat con messaggi non letti dall'utente corrente: una chat conta come
 * "non letta" se l'ultimo messaggio è più recente dell'ultimo visto localmente.
 *
 * `ultimo_messaggio_at` (server) coincide col `created_at` dell'ultimo messaggio
 * (stesso `now()` di transazione, vedi schema): quando l'utente apre la chat segna
 * come visto proprio quel timestamp, quindi al ritorno in home il conteggio si azzera.
 */
class GetUnreadChatsCountUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val badgeStateRepository: BadgeStateRepository
) {
    suspend operator fun invoke(): Result<Int> = runCatching {
        chatRepository.getChatsForCurrentUser().getOrThrow().count { chat ->
            val lastMessageAt = chat.lastMessageAt ?: return@count false
            val seen = badgeStateRepository.chatLastSeen(chat.chatId)
            // Il "visto" è persistito troncato ai millisecondi (SharedPreferences usa Long
            // via toEpochMilliseconds), mentre lastMessageAt arriva a precisione microsecondi
            // da Postgres (ultimo_messaggio_at). Confronto alla STESSA granularità (ms),
            // altrimenti uno scarto <1ms farebbe risultare "non letta" una chat già aperta.
            seen == null || lastMessageAt.toEpochMilliseconds() > seen.toEpochMilliseconds()
        }
    }
}
