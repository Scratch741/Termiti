package com.example.termiti

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Kroky rituálu kolosu (141 Probuzení, 142 Úder) jsou nesbíratelné (isPlaceholder),
 * ale v balíčku jsou to skutečné karty: rozhodnutí nad hromádkou (balíček, odhozené,
 * ruka) je musí nabízet. Nabídky z celé databáze karet (Rekrut, Goblin šaman…) ne.
 */
class ChainCardDecisionTest {

    private val allCards: List<Card> by lazy {
        val f = listOf("src/main/assets/cards.json", "app/src/main/assets/cards.json")
            .map(::File).first { it.exists() }
        CardRepository.parseCardsJson(f.readText())
    }

    private fun card(id: String) = allCards.first { it.id == id }

    private fun options(fx: CardEffect, self: PlayerState, opp: PlayerState = PlayerState()) =
        decisionOptions(fx, self, opp, allCards) { _, _ -> error("nepoužito") }

    @Test
    fun chooseTypeNeverOffersChainSteps() {
        for (type in listOf("Magie", "Útok", "Stavba", "Chaos")) repeat(300) {
            val ids = options(CardEffect.DecisionChooseType(type, 4, 2), PlayerState()).map { it.id }
            assertFalse("$type nabídl $ids", ids.any { it == "141" || it == "142" })
        }
    }

    @Test
    fun mineDecisionNeverOffersAwakening() {
        // Probuzení kolosu má 3× AddMine – Průzkum dolů ho dřív nabízel.
        repeat(500) {
            val ids = options(CardEffect.DecisionMine, PlayerState()).map { it.id }
            assertFalse("Průzkum dolů nabídl $ids", "141" in ids)
        }
    }

    @Test
    fun drawFromDeckOffersChainStepOnTop() {
        val self = PlayerState().also {
            it.deck.add(card("141"))
            it.deck.addAll(listOf("001", "002", "004", "005").map(::card))
        }
        val ids = options(CardEffect.DecisionDrawFromDeck(4), self).map { it.id }
        assertTrue("Probuzení kolosu chybí v $ids", "141" in ids)
    }

    @Test
    fun pileDecisionsStillHideTrapsAndDeadCards() {
        val self = PlayerState().also {
            it.deck.addAll(listOf("C37", "C38", "134").map(::card))
            it.deck.add(card("001"))
        }
        val ids = options(CardEffect.DecisionDrawFromDeck(4), self).map { it.id }
        assertTrue("nabídka $ids", ids == listOf("001"))
    }

    @Test
    fun burnAndCopyOfferChainSteps() {
        val withStep = PlayerState().also { it.deck.add(card("142")) }
        assertTrue(options(CardEffect.DecisionBurnOpponent(4), PlayerState(), withStep).any { it.id == "142" })
        assertTrue(options(CardEffect.DecisionFromDeck(4), withStep).any { it.id == "142" })
    }
}
