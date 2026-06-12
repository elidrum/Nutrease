package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.ChatPreview
import com.example.nutrease.domain.repository.BadgeStateRepository
import com.example.nutrease.domain.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetUnreadChatsCountUseCaseTest {

    private val chatRepository: ChatRepository = mockk()
    private val badgeStateRepository: BadgeStateRepository = mockk()
    private val useCase = GetUnreadChatsCountUseCase(chatRepository, badgeStateRepository)

    private fun preview(chatId: Long, lastMessageAt: Instant?) = ChatPreview(
        chatId = chatId,
        counterpartName = "Controparte $chatId",
        counterpartTaxCode = "CF$chatId",
        lastMessageText = "ciao",
        lastMessageAt = lastMessageAt
    )

    @Test
    fun `counts only chats whose last message is newer than last seen`() = runTest {
        val t10 = Instant.fromEpochMilliseconds(10)
        val t20 = Instant.fromEpochMilliseconds(20)
        coEvery { chatRepository.getChatsForCurrentUser() } returns Result.success(
            listOf(
                preview(1, t20), // visto a t10 < t20  → NON letta
                preview(2, t10), // visto a t10 == t10 → letta
                preview(3, t20), // mai vista (null)   → NON letta
                preview(4, null) // nessun messaggio   → non conta
            )
        )
        coEvery { badgeStateRepository.chatLastSeen(1) } returns t10
        coEvery { badgeStateRepository.chatLastSeen(2) } returns t10
        coEvery { badgeStateRepository.chatLastSeen(3) } returns null

        assertEquals(2, useCase().getOrThrow())
    }

    @Test
    fun `zero when every chat has been read up to its last message`() = runTest {
        val t = Instant.fromEpochMilliseconds(50)
        coEvery { chatRepository.getChatsForCurrentUser() } returns
            Result.success(listOf(preview(1, t), preview(2, t)))
        coEvery { badgeStateRepository.chatLastSeen(any()) } returns t

        assertEquals(0, useCase().getOrThrow())
    }

    @Test
    fun `chat read at millisecond granularity is not counted despite sub-millisecond server precision`() = runTest {
        // Regressione del badge chat sempre acceso: ultimo_messaggio_at di Postgres ha
        // precisione microsecondi, ma il "visto" è persistito troncato ai millisecondi
        // (toEpochMilliseconds). Confrontando a precisione piena la chat risultava "non letta"
        // per <1ms di scarto; il confronto va fatto alla stessa granularità (ms).
        val lastMessageAt = Instant.fromEpochSeconds(0, 123_456_000) // 123.456 ms (456µs oltre il ms)
        val seenTruncatedToMs = Instant.fromEpochMilliseconds(lastMessageAt.toEpochMilliseconds()) // 123 ms
        coEvery { chatRepository.getChatsForCurrentUser() } returns
            Result.success(listOf(preview(1, lastMessageAt)))
        coEvery { badgeStateRepository.chatLastSeen(1) } returns seenTruncatedToMs

        assertEquals(0, useCase().getOrThrow())
    }

    @Test
    fun `failure from chat repository propagates`() = runTest {
        coEvery { chatRepository.getChatsForCurrentUser() } returns
            Result.failure(IllegalStateException("boom"))

        assertTrue(useCase().isFailure)
    }
}
