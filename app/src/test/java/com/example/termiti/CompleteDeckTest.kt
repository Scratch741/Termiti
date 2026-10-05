package com.example.termiti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Automatické doplnění rozdělaného balíčku – viz [completeDeck]. */
class CompleteDeckTest {

    private val allCards: List<Card> by lazy {
        val f = listOf("src/main/assets/cards.json", "app/src/main/assets/cards.json")
            .map(::File).first { it.exists() }
        CardRepository.parseCardsJson(f.readText())
    }
    private val byId by lazy { allCards.associateBy { it.id } }

    @Test
    fun fillsToThirtyAndKeepsExistingCards() {
        val start = mapOf("001" to 3, "007" to 2, "002" to 2)   // 5 útok, 2 kámen
        repeat(50) {
            val filled = completeDeck(start, allCards) { it.rarity.maxCopies }
            assertEquals(30, filled.values.sum())
            start.forEach { (id, k) -> assertTrue("$id ubylo", filled.getValue(id) >= k) }
            filled.forEach { (id, k) ->
                val c = byId.getValue(id)
                assertTrue("$id přes limit", k <= c.rarity.maxCopies)
                assertTrue("$id je nesbíratelná nebo X-karta", !c.isPlaceholder && !c.isXCost)
                // Balíček bez magie a chaosu je nedostane
                assertTrue("$id má cizí surovinu", c.costType == ResourceType.ATTACK || c.costType == ResourceType.STONES)
            }
            // Poměr 5 : 2 zůstává zhruba zachovaný
            val attack = filled.entries.sumOf { (id, k) -> if (byId.getValue(id).costType == ResourceType.ATTACK) k else 0 }
            assertTrue("útoku je $attack", attack in 19..24)
        }
    }

    @Test
    fun stopsWhenNothingIsOwned() {
        val start = mapOf("001" to 1)
        val filled = completeDeck(start, allCards) { if (it.id == "001") 3 else 0 }
        assertEquals(mapOf("001" to 3), filled)
    }
}
