package com.example.nutrease.data.dto

import com.example.nutrease.domain.model.ChatPreview
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatPersonDto(
    @SerialName("Nome") val firstName: String,
    @SerialName("Cognome") val lastName: String,
    @SerialName("CodiceFiscale") val taxCode: String
)

@Serializable
data class ChatLastMessageDto(
    @SerialName("Testo") val text: String,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * Riga della tabella `chat` con embed PostgREST di entrambe le controparti
 * (`paziente`/`specialista`) e dell'ultimo messaggio (`messaggio`, ordinato
 * desc + limit 1 lato server). Le RLS consentono al paziente di leggere lo
 * specialista e viceversa, quindi entrambi gli embed risolvono sempre.
 */
@Serializable
data class ChatDto(
    @SerialName("IdChat") val id: Long,
    @SerialName("CodFiscalePaziente") val patientTaxCode: String,
    @SerialName("CodFiscaleSpecialista") val specialistTaxCode: String,
    @SerialName("ultimo_messaggio_at") val lastMessageAt: String? = null,
    @SerialName("paziente") val patient: ChatPersonDto,
    @SerialName("specialista") val specialist: ChatPersonDto,
    @SerialName("messaggio") val lastMessages: List<ChatLastMessageDto> = emptyList()
)

/**
 * Sceglie la controparte in base al codice fiscale dell'utente corrente: se
 * coincide con il paziente, la controparte è lo specialista, altrimenti il
 * paziente.
 */
fun ChatDto.toPreview(currentTaxCode: String): ChatPreview {
    val counterpart = if (patientTaxCode == currentTaxCode) specialist else patient
    val lastMessage = lastMessages.firstOrNull()
    return ChatPreview(
        chatId = id,
        counterpartName = "${counterpart.firstName} ${counterpart.lastName}",
        counterpartTaxCode = counterpart.taxCode,
        lastMessageText = lastMessage?.text,
        // L'istante dell'ultimo messaggio per ordinamento e badge "non letto" arriva dal
        // `created_at` del messaggio embeddato: è la STESSA colonna che la ChatScreen registra
        // come "visto" (MarkChatSeenUseCase), quindi il confronto in GetUnreadChatsCountUseCase
        // è sempre coerente. Fallback alla colonna denormalizzata `ultimo_messaggio_at` solo se
        // l'embed non porta il timestamp (non dovrebbe: la select lo richiede esplicitamente).
        lastMessageAt = (lastMessage?.createdAt ?: lastMessageAt)?.let { Instant.parse(it) }
    )
}