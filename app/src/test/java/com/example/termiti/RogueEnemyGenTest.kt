package com.example.termiti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Generátor roguelike soupeřů a obtížnosti (RogueEnemies.kt). */
class RogueEnemyGenTest {

    private val allCards: List<Card> by lazy {
        val f = listOf("src/main/assets/cards.json", "app/src/main/assets/cards.json")
            .map(::File).first { it.exists() }
        CardRepository.parseCardsJson(f.readText())
    }
    private val byId by lazy { allCards.associateBy { it.id } }

    @Test
    fun everyArchetypePresetExists() {
        val names = PRESET_DECKS.map { it.first }.toSet()
        for (a in RogueArchetype.entries) for (p in a.presets) assertTrue("$a: šablona '$p' chybí", p in names)
    }

    @Test
    fun enemyDecksRespectCapsAndBans() {
        val rnd = Random(7)
        for (a in RogueArchetype.entries) for (q in 0..20) repeat(5) {
            val deck = RogueEnemyGen.buildDeck(a, q, allCards, rnd)
            assertEquals("$a q$q: velikost", 30, deck.values.sum())
            val caps = RogueEnemyGen.caps(q)
            for (r in Rarity.entries) {
                val n = deck.entries.sumOf { (id, k) -> if (byId.getValue(id).rarity == r) k else 0 }
                assertTrue("$a q$q: $r $n > ${caps.of(r)}", n <= caps.of(r))
            }
            for ((id, k) in deck) {
                val c = byId.getValue(id)
                assertTrue("$a q$q: $id je zakázaná / nepoužitelná", RogueEnemyGen.usable(c))
                assertTrue("$a q$q: $id ${k}× > ${c.rarity.maxCopies}", k <= c.rarity.maxCopies)
            }
        }
    }

    @Test
    fun earlyEnemiesHaveNoEpicsOrLegendaries() {
        // Rytíř: první akt bez epických a legendárních karet
        for (b in 0 until RogueConfig.BATTLES_PER_ACT) {
            val boss = b == RogueConfig.BATTLES_PER_ACT - 1
            val caps = RogueEnemyGen.caps(RogueEnemyGen.quality(b, boss, RogueDifficulty.KNIGHT))
            assertEquals(0, caps.epic); assertEquals(0, caps.legendary)
        }
    }

    @Test
    fun archetypeNeverRepeatsBackToBack() {
        val rnd = Random(3)
        var last: RogueArchetype? = null
        repeat(200) {
            val a = RogueEnemyGen.pickArchetype(q = 10, avoid = last, rnd = rnd)
            assertTrue(a != last)
            last = a
        }
        // Při nízké kvalitě jen archetypy bez karet za chaos
        repeat(50) { assertTrue(RogueEnemyGen.pickArchetype(0, null, rnd).minQuality == 0) }
    }

    @Test
    fun difficultyUnlockChain() {
        val none = emptySet<String>()
        assertTrue(RogueDifficulty.APPRENTICE.isUnlocked(none))
        assertTrue(RogueDifficulty.KNIGHT.isUnlocked(none))
        assertFalse(RogueDifficulty.WARLORD.isUnlocked(none))
        assertFalse(RogueDifficulty.NIGHTMARE.isUnlocked(setOf("KNIGHT")))
        assertTrue(RogueDifficulty.WARLORD.isUnlocked(setOf("KNIGHT")))
        assertTrue(RogueDifficulty.NIGHTMARE.isUnlocked(setOf("KNIGHT", "WARLORD")))
        assertEquals(RogueDifficulty.WARLORD, RogueDifficulty.KNIGHT.unlocks)
        assertNull(RogueDifficulty.APPRENTICE.unlocks)
        assertNull(RogueDifficulty.NIGHTMARE.unlocks)
        // Těžší obtížnost = víc zlaťáků i balíčků
        RogueDifficulty.entries.zipWithNext().forEach { (a, b) ->
            assertTrue(b.totalRunGold > a.totalRunGold)
            assertTrue(b.finalPacks + b.firstClearPacks > a.finalPacks + a.firstClearPacks)
        }
        assertEquals(RogueDifficulty.KNIGHT, RogueDifficulty.fromName(null))
        assertEquals(RogueDifficulty.KNIGHT, RogueDifficulty.fromName("nesmysl"))
    }
}
