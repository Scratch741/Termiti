package com.example.termiti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Balíčky karet: losování obsahu, duplikáty → prach, zásoba neotevřených balíčků
 * a záruka legendární karty – viz [CardCollectionManager].
 */
class PackRollTest {

    private val allCards: List<Card> by lazy {
        val f = listOf("src/main/assets/cards.json", "app/src/main/assets/cards.json")
            .map(::File).first { it.exists() }
        CardRepository.parseCardsJson(f.readText())
    }
    private val pool by lazy { allCards.filter { !it.isBasic && !it.isPlaceholder } }

    @Test
    fun packHasFiveCardsAndAtLeastOneRarePlus() {
        val rnd = Random(1)
        repeat(2000) {
            val r = CardCollectionManager.rollPack(pool, mutableMapOf(), forceLegendary = false, random = rnd)
            assertEquals(CardCollectionManager.PACK_SIZE, r.cards.size)
            assertTrue("balíček bez vzácné+", r.cards.any { it.card.rarity != Rarity.COMMON })
            assertTrue("základní nebo zástupná karta v balíčku", r.cards.none { it.card.isBasic || it.card.isPlaceholder })
            assertFalse(r.pityUsed)
        }
    }

    @Test
    fun pityAlwaysGivesLegendary() {
        val rnd = Random(2)
        var pityPacks = 0
        repeat(500) {
            val r = CardCollectionManager.rollPack(pool, mutableMapOf(), forceLegendary = true, random = rnd)
            assertTrue("záruka nedala legendární", r.cards.any { it.card.rarity == Rarity.LEGENDARY })
            if (r.pityUsed) pityPacks++
        }
        // Většinou legendární nepadne sama, takže záruka musí zasahovat v naprosté většině balíčků
        assertTrue("záruka zasáhla jen ${pityPacks}×", pityPacks > 350)
    }

    @Test
    fun cardPositionDoesNotRevealTheGuaranteedSlot() {
        // Garantovaný slot se losuje poslední; po zamíchání nesmí být vzácná+ vždy na konci.
        val rnd = Random(3)
        var bestIsLast = 0
        val n = 2000
        repeat(n) {
            val r = CardCollectionManager.rollPack(pool, mutableMapOf(), forceLegendary = false, random = rnd)
            val best = r.cards.maxOf { it.card.rarity.ordinal }
            if (r.cards.last().card.rarity.ordinal == best && r.cards.count { it.card.rarity.ordinal == best } == 1) bestIsLast++
        }
        assertTrue("nejlepší karta je poslední v $bestIsLast z $n balíčků", bestIsLast < n * 0.5)
    }

    @Test
    fun duplicatesTurnIntoDustAndCollectionNeverExceedsMaxCopies() {
        val rnd = Random(4)
        val collection = mutableMapOf<String, Int>()
        var dust = 0
        var newCards = 0
        repeat(3000) {
            val before = collection.toMap()
            val r = CardCollectionManager.rollPack(pool, collection, forceLegendary = false, random = rnd)
            dust += r.totalDustGained
            assertEquals(r.cards.sumOf { it.dustGained }, r.totalDustGained)
            r.cards.forEach { g ->
                if (g.isDuplicate) {
                    assertEquals(g.card.rarity.dustValue, g.dustGained)
                    assertFalse(g.isNew)
                } else {
                    assertEquals(0, g.dustGained)
                }
                if (g.isNew) { newCards++; assertEquals("nová karta už ve sbírce byla", 0, before[g.card.id] ?: 0) }
            }
        }
        val byId = allCards.associateBy { it.id }
        collection.forEach { (id, k) -> assertTrue("$id: $k kopií", k in 1..byId.getValue(id).rarity.maxCopies) }
        // Po 3000 balíčcích je sbírka plná → každá sběratelská karta byla právě jednou „nová"
        assertEquals(pool.size, collection.size)
        assertEquals(pool.size, newCards)
        assertTrue(dust > 0)
    }

    @Test
    fun buyStoresPacksAndOpeningUsesThem() {
        PlayerProfileManager.save(PlayerProfile(name = "test", gold = 350))
        assertEquals(0, CardCollectionManager.unopenedPacks())
        assertNull("otevření bez balíčku", CardCollectionManager.openStoredPack(allCards, Random(5)))

        assertFalse("na 5 balíčků není zlato", CardCollectionManager.buyPacks(5))
        assertTrue(CardCollectionManager.buyPacks(3))
        assertEquals(50, PlayerProfileManager.profile!!.gold)
        assertEquals(3, CardCollectionManager.unopenedPacks())
        assertTrue("koupě nesmí měnit sbírku", PlayerProfileManager.profile!!.cardCollection.isEmpty())

        val r = CardCollectionManager.openStoredPack(allCards, Random(5))
        assertNotNull(r)
        assertEquals(2, CardCollectionManager.unopenedPacks())
        val p = PlayerProfileManager.profile!!
        assertEquals(r!!.cards.count { !it.isDuplicate }, p.cardCollection.values.sum())
        assertEquals(r.totalDustGained, p.dust)
        assertEquals(50, p.gold)
    }

    @Test
    fun legendaryIsGuaranteedWithinPityLimit() {
        val rnd = Random(6)
        PlayerProfileManager.save(PlayerProfile(name = "test", unopenedPacks = 3000))
        var sinceLegendary = 0
        var longest = 0
        var pityHits = 0
        repeat(3000) {
            val until = CardCollectionManager.packsUntilGuaranteedLegendary()
            assertEquals(CardCollectionManager.PITY_PACKS - sinceLegendary, until)
            val r = CardCollectionManager.openStoredPack(allCards, rnd)!!
            if (r.pityUsed) { pityHits++; assertEquals("záruka mimo poslední balíček", 1, until) }
            if (r.cards.any { it.card.rarity == Rarity.LEGENDARY }) sinceLegendary = 0 else sinceLegendary++
            longest = maxOf(longest, sinceLegendary)
            assertEquals(sinceLegendary, PlayerProfileManager.profile!!.packsSinceLegendary)
        }
        assertTrue("série bez legendární: $longest", longest <= CardCollectionManager.PITY_PACKS - 1)
        assertTrue("záruka za 3000 balíčků nikdy nezasáhla", pityHits > 0)
        assertEquals(0, CardCollectionManager.unopenedPacks())
    }

    @Test
    fun collectionProgressCountsBasicAndOwnedCards() {
        PlayerProfileManager.save(PlayerProfile(name = "test"))
        val (ownedEmpty, total) = CardCollectionManager.collectionProgress(allCards)
        assertEquals(allCards.count { it.isBasic && !it.isPlaceholder }, ownedEmpty)
        val one = pool.first()
        PlayerProfileManager.save(PlayerProfile(name = "test", cardCollection = mapOf(one.id to 1)))
        assertEquals(ownedEmpty + 1, CardCollectionManager.collectionProgress(allCards).first)
        assertTrue(total >= pool.size)
    }
}
