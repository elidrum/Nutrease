package com.example.nutrease.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.usecase.GetRecentMessagesUseCase
import com.example.nutrease.domain.usecase.MarkChatSeenUseCase
import com.example.nutrease.domain.usecase.ObserveNewMessagesUseCase
import com.example.nutrease.domain.usecase.SendMessageUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val getRecentMessagesUseCase: GetRecentMessagesUseCase = mockk()
    private val sendMessageUseCase: SendMessageUseCase = mockk()
    private val observeNewMessagesUseCase: ObserveNewMessagesUseCase = mockk()
    private val markChatSeenUseCase: MarkChatSeenUseCase = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private val realtimeFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 8)

    private fun message(id: Long, text: String) = ChatMessage(
        id = id,
        chatId = 1L,
        senderUid = "uid-me",
        text = text,
        createdAt = Instant.fromEpochMilliseconds(id)
    )

    private fun savedState(): SavedStateHandle = SavedStateHandle(
        mapOf(
            ChatViewModel.ARG_CHAT_ID to 1L,
            ChatViewModel.ARG_COUNTERPART_NAME to "Mario Rossi"
        )
    )

    private fun buildViewModel() = ChatViewModel(
        getRecentMessagesUseCase,
        sendMessageUseCase,
        observeNewMessagesUseCase,
        markChatSeenUseCase,
        authRepository,
        savedState()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { authRepository.getCurrentUser() } returns
            AuthUser(id = "uid-me", email = "me@x.it", role = UserRole.PATIENT, taxCode = "P1")
        coEvery { getRecentMessagesUseCase(1L) } returns Result.success(emptyList())
        every { observeNewMessagesUseCase(1L) } returns realtimeFlow
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `realtime echo of an optimistically appended message is deduplicated`() = runTest {
        val sent = message(10L, "Ciao")
        coEvery { sendMessageUseCase(1L, "Ciao") } returns Result.success(sent)

        val viewModel = buildViewModel()
        advanceUntilIdle() // initial load + subscription

        viewModel.sendMessage("Ciao")
        advanceUntilIdle() // optimistic append

        // Stesso messaggio rispedito dal canale Realtime (echo del proprio INSERT)
        realtimeFlow.tryEmit(sent)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.messages.size)
            assertEquals(10L, state.messages.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `counterpart name and current user uid are exposed in state`() = runTest {
        coEvery { sendMessageUseCase(any(), any()) } returns Result.success(message(1L, "x"))

        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Mario Rossi", state.counterpartName)
            assertEquals("uid-me", state.currentUserUid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `send failure surfaces the transient send error`() = runTest {
        coEvery { sendMessageUseCase(1L, "Ciao") } returns
            Result.failure(IllegalStateException("offline"))

        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.sendMessage("Ciao")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(true, state.sendFailed)
            assertEquals(0, state.messages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}