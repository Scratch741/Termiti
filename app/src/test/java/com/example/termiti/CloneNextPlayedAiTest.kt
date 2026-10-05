package com.example.termiti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * AI a Chaotická replikace (CloneNextPlayed): kopíruje jen karty, které hru posunou
 * a které se stihne líznout – viz AiEngine.cloneValue.
 */
class CloneNextPlayedAiTest {

    private fun replikace(id: String = "116_a") = Card(
        id = id, name = "Chaotická replikace", description = "",
        cost = 2, costType = ResourceType.CHAOS, isCombo = true,
        effects = listOf(CardEffect.CloneNextPlayed(2))
    )

    private fun rychlaMagie(id: String = "037_a") = Card(
        id = id, name = "Rychlá magie", description = "",
        cost = 1, costType = ResourceType.MAGIC, isCombo = true,
        effects = listOf(CardEffect.AddResource(ResourceType.MAGIC, 4))
    )

    private fun temnyPrenos(id: String = "099_a") = Card(
        id = id, name = "Temný přenos", description = "",
        cost = 8, costType = ResourceType.MAGIC,
        effects = listOf(CardEffect.StealCastle(10))
    )

    private fun filler(i: Int) = Card(
        id = "fill_$i", name = "Výplň", description = "",
        cost = 3, costType = ResourceType.STONES, effects = listOf(CardEffect.BuildWall(5))
    )

    private fun ai(castle: Int, deckSize: Int, vararg hand: Card) = PlayerState(castleHP = castle, wallHP = 10).also {
        it.resources[ResourceType.CHAOS] = 5
        it.resources[ResourceType.MAGIC] = 10
        it.hand.addAll(hand)
        repeat(deckSize) { i -> it.deck.add(filler(i)) }
    }

    private fun opponent(castle: Int) = PlayerState(castleHP = castle, wallHP = 10).also {
        repeat(15) { i -> it.deck.add(filler(100 + i)) }
    }

    @Test
    fun lateGameDoesNotReplicateQuickMagic() {
        val r = replikace()
        // Soupeř je 8 od výhry stavbou (cíl 100) → hra končí, kopie Rychlé magie by jen ředily balíček
        repeat(20) {
            val action = aiChooseAction(ai(castle = 50, deckSize = 12, r, rychlaMagie()), opponent(castle = 92),
                                        aiWinTarget = 100, playerWinTarget = 100)
            assertNotEquals("Replikace na Rychlou magii v pozdní hře: $action", AiAction.Play(r), action)
        }
    }

    @Test
    fun activeReplicationPicksDarkTransferOverQuickMagic() {
        val tp = temnyPrenos()
        repeat(20) {
            val state = ai(castle = 50, deckSize = 20, rychlaMagie(), tp).also { it.cloneNextPlayed = 2 }
            val action = aiChooseAction(state, opponent(castle = 50), aiWinTarget = 100, playerWinTarget = 100)
            assertEquals(AiAction.Play(tp), action)
        }
    }

    @Test
    fun earlyGamePlaysReplicationBeforeValuableTarget() {
        val r = replikace()
        repeat(20) {
            val action = aiChooseAction(ai(castle = 50, deckSize = 20, r, temnyPrenos()), opponent(castle = 50),
                                        aiWinTarget = 100, playerWinTarget = 100)
            assertEquals(AiAction.Play(r), action)
        }
    }
}
