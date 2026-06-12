package com.example.nutrease.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.nutrease.domain.model.ChatMessage
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test

class MessageBubbleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun message(text: String) = ChatMessage(
        id = 1L,
        chatId = 1L,
        senderUid = "uid",
        text = text,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun rendersOwnMessageText() {
        composeTestRule.setContent {
            NutreaseTheme {
                MessageBubble(message = message("Ciao, come stai?"), isMine = true)
            }
        }
        composeTestRule.onNodeWithText("Ciao, come stai?").assertIsDisplayed()
    }

    @Test
    fun rendersCounterpartMessageText() {
        composeTestRule.setContent {
            NutreaseTheme {
                MessageBubble(message = message("Tutto bene, grazie"), isMine = false)
            }
        }
        composeTestRule.onNodeWithText("Tutto bene, grazie").assertIsDisplayed()
    }
}