package com.example.nutrease.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nutrease.ui.theme.NutreaseTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeActionCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersTitleAndSubtitle_andInvokesOnClick() {
        var clicked = false
        composeTestRule.setContent {
            NutreaseTheme {
                HomeActionCard(
                    title = "Diario alimentare",
                    subtitle = "Registra pasti e sintomi",
                    icon = Icons.Default.Restaurant,
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Diario alimentare").assertIsDisplayed()
        composeTestRule.onNodeWithText("Registra pasti e sintomi").assertIsDisplayed()

        composeTestRule.onNodeWithText("Diario alimentare").performClick()
        assertTrue(clicked)
    }

    @Test
    fun showsBadge_whenCountIsPositive() {
        composeTestRule.setContent {
            NutreaseTheme {
                HomeActionCard(
                    title = "Richieste",
                    subtitle = null,
                    icon = Icons.Default.Restaurant,
                    onClick = {},
                    badgeCount = 3
                )
            }
        }

        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }
}