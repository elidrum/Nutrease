package com.example.nutrease.ui.screens.chat

import app.cash.turbine.test
import com.example.nutrease.domain.model.ChatPreview
import com.example.nutrease.domain.usecase.GetChatsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    private val getChatsUseCase: GetChatsUseCase = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private fun preview(id: Long, name: String) = ChatPreview(
        chatId = id,
        counterpartName = name,
        counterpartTaxCode = "CF$id",
        lastMessageText = "Ciao",
        lastMessageAt = Instant.fromEpochMilliseconds(id)
    )

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init loads chats`() = runTest {
        coEvery { getChatsUseCase() } returns Result.success(listOf(preview(1L, "Anna"), preview(2L, "Luca")))
        val viewModel = ChatListViewModel(getChatsUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.chats.size)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure surfaces error`() = runTest {
        coEvery { getChatsUseCase() } returns Result.failure(Exception("rete assente"))
        val viewModel = ChatListViewModel(getChatsUseCase)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("rete assente", state.error)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}