package com.example.termiti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI zahodí kartu s efektem „+N suroviny" při zahození (Zoufalý žold), když si tím
 * ještě tento tah zpřístupní kartu, na kterou teď nemá – viz AiEngine.discardToAfford.
 */
class AiDiscardToAffordTest {

    private fun zold(id: String = "132_a") = Card(
        id = id, name = "Zoufalý žold", description = "",
        cost = 2, costType = ResourceType.ATTACK,
        effects = listOf(CardEffect.AttackPlayer(5)),
        discardEffects = listOf(CardEffect.AddResource(ResourceType.ATTACK, 3))
    )

    private fun expensiveHit(id: String = "big_a") = Card(
        id = id, name = "Drahý úder", description = "",
        cost = 11, costType = ResourceType.ATTACK,
        effects = listOf(CardEffect.AttackCastle(20))
    )

    private fun ai(attack: Int, vararg hand: Card) = PlayerState(castleHP = 30, wallHP = 10).also {
        it.resources[ResourceType.ATTACK] = attack
        it.hand.addAll(hand)
        repeat(10) { i -> it.deck.add(zold("deck_$i")) }
    }

    private fun opponent(castle: Int) = PlayerState(castleHP = castle, wallHP = 0).also {
        repeat(10) { i -> it.deck.add(zold("opp_$i")) }
    }

    @Test
    fun discardsToAffordLethalCard() {
        val z = zold()
        // 9 útoku: Žold (2) jde zahrát, úder za 11 ne; zahození Žoldu (+3) → 12 ≥ 11 a úder zabije.
        val action = aiChooseAction(ai(9, z, expensiveHit()), opponent(castle = 15))
        assertEquals(AiAction.Discard(z), action)
    }

    @Test
    fun noDiscardWhenAlreadyDiscardedThisTurn() {
        val z = zold()
        val action = aiChooseAction(ai(9, z, expensiveHit()), opponent(castle = 15), canDiscard = false)
        assertTrue("bez možnosti zahodit má Žold zahrát, ne zahodit: $action", action == AiAction.Play(z))
    }

    @Test
    fun playsZoldWhenNothingElseUsesTheResource() {
        val z = zold()
        // Žádná jiná útočná karta v ruce → útok navíc by nebylo kam utratit, Žold se zahraje.
        val action = aiChooseAction(ai(9, z), opponent(castle = 40))
        assertEquals(AiAction.Play(z), action)
    }

    @Test
    fun discardsZoldWhenAnotherAttackCardWillSpendIt() {
        val z = zold()
        val cheapHit = Card(
            id = "hit_a", name = "Úder", description = "",
            cost = 3, costType = ResourceType.ATTACK,
            effects = listOf(CardEffect.AttackPlayer(9))
        )
        // V ruce je další útočná karta → +3 útoku ze zahození se využije (discardForResources).
        val action = aiChooseAction(ai(9, z, cheapHit), opponent(castle = 40))
        assertEquals(AiAction.Discard(z), action)
    }
}
