package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.ChatDto
import com.example.nutrease.data.dto.ChatLastMessageDto
import com.example.nutrease.data.dto.ChatPersonDto
import com.example.nutrease.data.dto.MessageDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.data.dto.toPreview
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRepositoryImplTest {

    private fun person(suffix: String) = ChatPersonDto(
        firstName = "Nome$suffix",
        lastName = "Cognome$suffix",
        taxCode = "CF$suffix"
    )

    @Test
    fun `toPreview takes lastMessageAt from the embedded message, not the denormalized column`() {
        // L'embed `messaggio.created_at` è la STESSA colonna che la ChatScreen registra come
        // "visto": il badge "non letto" deve confrontare quella, non `ultimo_messaggio_at`.
        val preview = ChatDto(
            id = 1L,
            patientTaxCode = "CFp",
            specialistTaxCode = "CFs",
            lastMessageAt = "2026-06-10T08:00:00Z",          // colonna denormalizzata (diversa)
            patient = person("p"),
            specialist = person("s"),
            lastMessages = listOf(ChatLastMessageDto(text = "ciao", createdAt = "2026-06-10T09:30:00Z"))
        ).toPreview(currentTaxCode = "CFp")

        assertEquals(Instant.parse("2026-06-10T09:30:00Z"), preview.lastMessageAt)
        assertEquals("ciao", preview.lastMessageText)
        // Controparte del paziente = lo specialista.
        assertEquals("Nomes Cognomes", preview.counterpartName)
    }

    @Test
    fun `toPreview falls back to ultimo_messaggio_at when the embed has no timestamp`() {
        val preview = ChatDto(
            id = 1L,
            patientTaxCode = "CFp",
            specialistTaxCode = "CFs",
            lastMessageAt = "2026-06-10T08:00:00Z",
            patient = person("p"),
            specialist = person("s"),
            lastMessages = emptyList() // nessun messaggio embeddato
        ).toPreview(currentTaxCode = "CFp")

        assertEquals(Instant.parse("2026-06-10T08:00:00Z"), preview.lastMessageAt)
    }

    @Test
    fun `toDomain maps fields and parses created_at`() {
        val message = MessageDto(
            id = 5L,
            chatId = 2L,
            senderUid = "uid-1",
            text = "ciao",
            createdAt = "2026-05-31T10:15:30Z"
        ).toDomain()

        assertEquals(5L, message.id)
        assertEquals(2L, message.chatId)
        assertEquals("uid-1", message.senderUid)
        assertEquals("ciao", message.text)
        assertEquals(Instant.parse("2026-05-31T10:15:30Z"), message.createdAt)
    }

    @Test
    fun `null id and created_at fall back to defaults`() {
        // Caso INSERT prima della risposta server: id e created_at assenti.
        val message = MessageDto(chatId = 2L, senderUid = "uid-1", text = "x").toDomain()

        assertEquals(0L, message.id)
        assertEquals(Instant.fromEpochMilliseconds(0), message.createdAt)
    }
}