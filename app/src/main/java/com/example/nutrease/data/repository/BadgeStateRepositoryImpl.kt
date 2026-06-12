package com.example.nutrease.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.nutrease.domain.repository.BadgeStateRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * Implementazione del [BadgeStateRepository] su [android.content.SharedPreferences]:
 * stato leggero "letto/visto" che sopravvive al riavvio dell'app. Niente rete, ma le
 * letture/scritture sono comunque su [Dispatchers.IO] per coerenza col resto del data layer.
 *
 * Lo stato è **scopato per UID della sessione corrente**: due account sullo stesso device
 * non condividono il "visto". Senza questo scope, il timestamp segnato come letto da un
 * utente (es. lo specialista che invia un messaggio) nascondeva il badge all'altro utente
 * (il paziente, al login sullo stesso telefono) → chat sempre "già letta".
 */
class BadgeStateRepositoryImpl(
    context: Context,
    private val supabase: SupabaseClient
) : BadgeStateRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun chatLastSeen(chatId: Long): Instant? = withContext(Dispatchers.IO) {
        val millis = prefs.getLong(chatKey(chatId), NEVER)
        if (millis == NEVER) null else Instant.fromEpochMilliseconds(millis)
    }

    override suspend fun setChatLastSeen(chatId: Long, lastMessageAt: Instant): Unit =
        withContext(Dispatchers.IO) {
            val key = chatKey(chatId)
            val candidate = lastMessageAt.toEpochMilliseconds()
            // Monotòno: non riportare mai indietro il "visto" (evita falsi non-letti).
            if (candidate > prefs.getLong(key, NEVER)) {
                prefs.edit { putLong(key, candidate) }
            }
        }

    override suspend fun seenAcceptedRequestIds(): Set<Long> = withContext(Dispatchers.IO) {
        prefs.getStringSet(acceptedSeenKey(), emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    override suspend fun markAcceptedRequestsSeen(ids: Set<Long>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val key = acceptedSeenKey()
        // getStringSet restituisce un set immutabile da non modificare: ne creo una copia.
        val merged = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        merged.addAll(ids.map { it.toString() })
        prefs.edit { putStringSet(key, merged) }
    }

    /** UID della sessione corrente (o [ANON] se non loggato): prefissa tutte le chiavi
     *  così lo stato badge resta separato per account sullo stesso device. */
    private fun currentUid(): String = supabase.auth.currentUserOrNull()?.id ?: ANON

    private fun chatKey(chatId: Long) = "chat_seen_${currentUid()}_$chatId"

    private fun acceptedSeenKey() = "accepted_seen_ids_${currentUid()}"

    companion object {
        private const val PREFS_NAME = "badge_state"
        private const val NEVER = -1L
        private const val ANON = "anon"
    }
}
