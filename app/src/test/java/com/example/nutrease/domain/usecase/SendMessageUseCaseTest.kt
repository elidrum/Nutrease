package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.domain.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendMessageUseCaseTest {

    private val chatRepository: ChatRepository = mockk()
    private val useCase = SendMessageUseCase(chatRepository)

    private fun fakeMessage(text: String) = ChatMessage(
        id = 1L,
        chatId = 7L,
        senderUid = "uid-1",
        text = text,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun `valid text is trimmed before reaching the repository`() = runTest {
        coEvery { chatRepository.sendMessage(7L, "Ciao") } returns
            Result.success(fakeMessage("Ciao"))

        val result = useCase(7L, "  Ciao  ")

        assertTrue(result.isSuccess)
        coVerify { chatRepository.sendMessage(7L, "Ciao") }
    }

    @Test
    fun `empty text is rejected`() = runTest {
        val result = useCase(7L, "")

        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any()) }
    }

    @Test
    fun `blank text is rejected`() = runTest {
        val result = useCase(7L, "    ")

        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any()) }
    }

    @Test
    fun `text exceeding 2000 chars is rejected`() = runTest {
        val result = useCase(7L, "x".repeat(2001))

        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any()) }
    }

    @Test
    fun `text of exactly 2000 chars is accepted`() = runTest {
        val text = "y".repeat(2000)
        coEvery { chatRepository.sendMessage(7L, text) } returns
            Result.success(fakeMessage(text))

        val result = useCase(7L, text)

        assertTrue(result.isSuccess)
        coVerify { chatRepository.sendMessage(7L, text) }
    }
}