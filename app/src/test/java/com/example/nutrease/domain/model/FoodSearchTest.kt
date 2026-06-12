package com.example.nutrease.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchTest {

    // --- rank(): end-to-end behaviour -----------------------------------------------------

    @Test
    fun `tolerates a small typo`() {
        val results = FoodSearch.rank("spagetti", foods("Spaghetti", "Insalata", "Riso")).names()
        // "spagetti" -> "spaghetti" is one edit; the unrelated foods are filtered out.
        assertEquals(listOf("Spaghetti"), results)
    }

    @Test
    fun `does not fuzzy-match very short queries`() {
        // "pst" is 3 chars -> fuzzy disabled, and it is neither a prefix nor a substring of "pasta".
        val results = FoodSearch.rank("pst", foods("Pasta")).names()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `multi-word query matches words in any order and drops partial matches`() {
        val results = FoodSearch.rank(
            "pasta bolo",
            foods("Pasta", "Pasta al ragù alla bolognese", "Bolo di carne")
        ).names()
        // Both typed words must match: only the bolognese pasta has "pasta" AND something starting with "bolo".
        assertEquals(listOf("Pasta al ragù alla bolognese"), results)
        assertFalse(results.contains("Pasta"))
    }

    @Test
    fun `ignores accents`() {
        val results = FoodSearch.rank("ragu", foods("Pasta al ragù alla bolognese", "Riso")).names()
        assertEquals(listOf("Pasta al ragù alla bolognese"), results)
    }

    @Test
    fun `ranks tighter matches first (adherence)`() {
        val results = FoodSearch.rank(
            "past",
            foods("Pasta al ragù alla bolognese", "Pasta", "Pasta alla carbonara")
        ).names()
        // All three are prefix matches; the shortest ("Pasta") wins, longest comes last.
        assertEquals(
            listOf("Pasta", "Pasta alla carbonara", "Pasta al ragù alla bolognese"),
            results
        )
    }

    @Test
    fun `exact name ranks above prefix matches`() {
        val results = FoodSearch.rank("pasta", foods("Pasta alla carbonara", "Pasta")).names()
        assertEquals(listOf("Pasta", "Pasta alla carbonara"), results)
    }

    @Test
    fun `blank query returns nothing`() {
        assertTrue(FoodSearch.rank("   ", foods("Pasta")).isEmpty())
    }

    // --- helpers under test ---------------------------------------------------------------

    @Test
    fun `normalize lowercases and strips diacritics`() {
        assertEquals("ragu", FoodSearch.normalize("RAGÙ"))
        assertEquals("pero", FoodSearch.normalize("Però"))
        assertEquals("pasta", FoodSearch.normalize("  Pasta  "))
    }

    @Test
    fun `tokenize splits on punctuation and whitespace`() {
        assertEquals(listOf("cereali", "muesli"), FoodSearch.tokenize("cereali/muesli"))
        assertEquals(listOf("asparago", "verde", "crudo"), FoodSearch.tokenize("asparago (verde) crudo"))
    }

    @Test
    fun `levenshtein counts single-character edits`() {
        assertEquals(0, FoodSearch.levenshtein("pasta", "pasta"))
        assertEquals(1, FoodSearch.levenshtein("spagetti", "spaghetti")) // one insertion
        assertEquals(3, FoodSearch.levenshtein("kitten", "sitting"))
        assertEquals(3, FoodSearch.levenshtein("", "abc"))
    }

    @Test
    fun `max edit distance grows with word length`() {
        assertEquals(0, FoodSearch.maxEditDistance(3))
        assertEquals(1, FoodSearch.maxEditDistance(4))
        assertEquals(1, FoodSearch.maxEditDistance(6))
        assertEquals(2, FoodSearch.maxEditDistance(7))
    }

    private fun foods(vararg names: String): List<Food> = names.map { name ->
        Food(
            id = name.hashCode(),
            name = name,
            category = null,
            lactosePer100g = 0.0,
            sorbitolPer100g = 0.0,
            glutenPer100g = 0.0,
            caloriesPer100g = 0.0,
            unitConversions = emptyMap()
        )
    }

    private fun List<Food>.names(): List<String> = map { it.name }
}
