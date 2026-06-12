package com.example.nutrease.domain.model

import kotlin.math.abs

/**
 * Motore di ricerca alimenti in puro Kotlin: data la query digitata e la lista degli
 * alimenti, scarta quelli che non corrispondono e ordina i restanti per "rilevanza"
 * (quanto bene il nome combacia con ciò che l'utente ha scritto). Vive in `domain/`
 * per restare privo di Android e portabile al futuro client Flutter; tutta la logica
 * di matching sta qui, in un unico punto testabile.
 *
 * È robusto a due casi che il vecchio `ILIKE '%query%'` non gestiva:
 *  - piccoli typo ("spagetti" -> "Spaghetti") tramite distanza di edit limitata (Levenshtein);
 *  - query multi-parola con parole in qualsiasi ordine ("pasta bolo" -> "Pasta al ragù
 *    alla bolognese") confrontando ogni parola digitata col nome in modo indipendente.
 *
 * Obiettivo del ranking ("aderenza"): match più stretti e corti prima
 * ("past" -> "Pasta" prima di "Pasta al ragù alla bolognese").
 */
object FoodSearch {

    // --- Pesi di scoring -------------------------------------------------------------------
    // Punteggio per singola parola della query (quanto bene UNA parola digitata combacia
    // col nome dell'alimento). Più alto = meglio.
    private const val WORD_EXACT = 1.0   // la parola digitata È una parola intera del nome
    private const val WORD_PREFIX = 0.9  // una parola del nome inizia con quella digitata
    private const val SUBSTRING = 0.7    // la parola digitata appare ovunque dentro il nome
    private const val FUZZY_BASE = 0.6   // ridotto in proporzione ai typo (vedi tokenScore)

    // Bonus sul nome intero, aggiunti una volta per alimento sopra i punteggi per-parola.
    private const val EXACT_NAME_BONUS = 2.0   // il nome intero coincide con l'intera query
    private const val PREFIX_NAME_BONUS = 1.0  // il nome inizia con l'intera query

    /**
     * Filtra [foods] a quelli che corrispondono a [query] e li ritorna ordinati per
     * rilevanza. Punto d'ingresso pubblico usato dallo UseCase.
     */
    fun rank(query: String, foods: List<Food>): List<Food> {
        val normalizedQuery = normalize(query)
        val queryTokens = tokenize(normalizedQuery)
        // Niente digitato (o soli separatori) -> niente da cercare.
        if (queryTokens.isEmpty()) return emptyList()

        return foods
            // Assegna un punteggio a ogni alimento; `scoreFood` ritorna null per quelli da scartare.
            .mapNotNull { food ->
                scoreFood(queryTokens, normalizedQuery, food)?.let { Scored(food, it) }
            }
            // Ordina per: rilevanza migliore, poi nome più corto (match più stretto), poi A→Z.
            .sortedWith(
                compareByDescending<Scored> { it.relevance }
                    .thenBy { it.food.name.length }
                    .thenBy { it.food.name.lowercase() }
            )
            .map { it.food }
    }

    /** Alimento accoppiato alla sua rilevanza calcolata, usato solo durante l'ordinamento. */
    private data class Scored(val food: Food, val relevance: Double)

    /**
     * Rilevanza di un alimento per la query data, o `null` se l'alimento non corrisponde.
     *
     * Regola (semantica AND): OGNI parola digitata dall'utente deve trovare un riscontro
     * nel nome; se una sola parola non trova nulla, l'alimento è scartato. È questo che fa
     * sì che "pasta bolo" tenga "Pasta al ragù alla **bolo**gnese" e scarti la sola "Pasta".
     */
    internal fun scoreFood(queryTokens: List<String>, normalizedQuery: String, food: Food): Double? {
        val normalizedName = normalize(food.name)
        val nameTokens = tokenize(normalizedName)

        var relevance = 0.0
        for (qt in queryTokens) {
            val tokenScore = tokenScore(qt, nameTokens, normalizedName)
            if (tokenScore == 0.0) return null // una parola digitata senza riscontro -> escludi
            relevance += tokenScore
        }

        // Premia i nomi allineati all'intera stringa di ricerca (tiene in cima exact/prefix).
        when {
            normalizedName == normalizedQuery -> relevance += EXACT_NAME_BONUS
            normalizedName.startsWith(normalizedQuery) -> relevance += PREFIX_NAME_BONUS
        }
        return relevance
    }

    /**
     * Miglior punteggio (0.0 = nessun match) di una singola parola della query [qt]
     * contro il nome dell'alimento. Prova prima il segnale più forte e si ferma al primo
     * riscontro: parola esatta > prefisso di parola > sottostringa > fuzzy.
     */
    internal fun tokenScore(qt: String, nameTokens: List<String>, normalizedName: String): Double {
        // 1) La parola digitata È una delle parole del nome.
        if (nameTokens.any { it == qt }) return WORD_EXACT
        // 2) Una parola del nome INIZIA con quella digitata ("bolo" -> "bolognese").
        if (nameTokens.any { it.startsWith(qt) }) return WORD_PREFIX
        // 3) La parola digitata appare da qualche parte nel nome (anche a metà parola).
        if (normalizedName.contains(qt)) return SUBSTRING

        // 4) Fallback fuzzy: tollera qualche typo, ma solo per parole abbastanza lunghe
        //    (le corte combacerebbero con quasi tutto -> rumore).
        val maxDistance = maxEditDistance(qt.length)
        if (maxDistance == 0) return 0.0

        var bestDistance = Int.MAX_VALUE
        for (nt in nameTokens) {
            // Pre-filtro economico: se le lunghezze differiscono più dei typo ammessi, la
            // distanza di edit non può rientrare nel budget, quindi salta il calcolo completo.
            if (abs(nt.length - qt.length) > maxDistance) continue
            val distance = levenshtein(qt, nt)
            if (distance < bestDistance) bestDistance = distance
        }

        // Entro il budget -> punteggio parziale che cala al crescere dei typo.
        return if (bestDistance <= maxDistance) FUZZY_BASE * (1.0 - bestDistance.toDouble() / qt.length)
        else 0.0
    }

    /** Quanti typo tolleriamo per una parola di lunghezza [length]. Tarato per evitare falsi match. */
    internal fun maxEditDistance(length: Int): Int = when {
        length <= 3 -> 0 // troppo corta: niente fuzzy
        length <= 6 -> 1 // fino a un typo
        else -> 2        // fino a due typo
    }

    // --- Normalizzazione del testo ---------------------------------------------------------

    /** Minuscole, accenti italiani rimossi e trim: così "Ragù" e "ragu" risultano uguali. */
    internal fun normalize(s: String): String {
        val lower = s.lowercase().trim()
        val sb = StringBuilder(lower.length)
        for (c in lower) sb.append(ACCENTS[c] ?: c)
        return sb.toString()
    }

    /**
     * Divide una stringa normalizzata in parole, scartando i separatori (spazi,
     * punteggiatura, …). Mappa di caratteri esplicita invece di `java.text.Normalizer`
     * per restare portabile verso Flutter.
     */
    internal fun tokenize(normalized: String): List<String> =
        normalized.split(TOKEN_SEPARATOR).filter { it.isNotEmpty() }

    private val TOKEN_SEPARATOR = Regex("[^a-z0-9]+")

    private val ACCENTS: Map<Char, Char> = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a',
        'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n'
    )

    // --- Distanza di edit ------------------------------------------------------------------

    /**
     * Distanza di Levenshtein: il numero minimo di inserimenti, cancellazioni o
     * sostituzioni di singoli caratteri per trasformare [a] in [b]. Versione classica a
     * programmazione dinamica con due righe rotanti (memoria O(b.length) invece della
     * matrice completa). Gli input sono singole parole, quindi il costo è trascurabile.
     */
    internal fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        // previous[j] = distanza tra a[0..i-1] e b[0..j-1] per la riga precedente i-1.
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i // trasformare i primi i caratteri di `a` in "" costa i cancellazioni
            for (j in 1..b.length) {
                val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,                   // cancella a[i-1]
                    current[j - 1] + 1,                // inserisci b[j-1]
                    previous[j - 1] + substitutionCost // mantieni o sostituisci
                )
            }
            // Riusa gli array: questa riga diventa la "precedente" per l'iterazione successiva.
            val tmp = previous; previous = current; current = tmp
        }
        return previous[b.length]
    }
}
