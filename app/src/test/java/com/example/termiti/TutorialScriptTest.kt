package com.example.termiti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Přehraje skript tutoriálu ([TUTORIAL_STEPS], [TUTORIAL_PLAYER_ORDER], [TUTORIAL_AI_ORDER])
 * nad herním enginem. Hlídá, že každou kartu, kterou krok chce, má hráč / soupeř v tu chvíli
 * v ruce a zaplatí ji, a že skript skončí výhrou. Po změně ceny nebo efektu některé
 * z karet skriptu tenhle test spadne dřív, než by se v tutoriálu zasekl hráč.
 */
class TutorialScriptTest {

    private val allCards: List<Card> by lazy {
        val f = listOf("src/main/assets/cards.json", "app/src/main/assets/cards.json")
            .map(::File).first { it.exists() }
        CardRepository.parseCardsJson(f.readText())
    }

    private fun res(p: PlayerState, t: ResourceType) = p.resources[t] ?: 0

    /** Zaplatí a zahraje kartu [baseId] z ruky [me]; vrací, jestli je to kombo. */
    private fun play(me: PlayerState, opp: PlayerState, baseId: String): Boolean {
        val card = me.hand.firstOrNull { it.baseId == baseId } ?: error("Karta $baseId není v ruce: ${me.hand.map { it.baseId }}")
        assertTrue("Na $baseId chybí ${card.costType}: ${res(me, card.costType)} < ${card.effectiveCost}",
            res(me, card.costType) >= card.effectiveCost)
        me.preCostResources = me.resources.toMap()
        me.resources[card.costType] = res(me, card.costType) - card.effectiveCost
        me.hand.remove(card)
        me.discardPile.add(card)
        applyEffects(card.effects, me, opp, allCards)
        return card.isCombo
    }

    @Test
    fun scriptPlaysOutToVictory() {
        val opponent = CampaignData.locations.first().opponents.first()

        // Stejné sestavení jako GameViewModel.createCampaignState v tutoriálu
        val player = PlayerState(castleHP = 30, wallHP = 15).also {
            it.deck.addAll(tutorialPlayerDeck(allCards).withUniqueIds())
            it.drawCards(4)
        }
        val ai = PlayerState(castleHP = opponent.aiCastle, wallHP = opponent.aiWall, maxWall = opponent.aiMaxWall).also {
            it.deck.addAll(TUTORIAL_AI_ORDER.map { id -> allCards.first { c -> c.id == id } }.withUniqueIds())
            it.drawCards(4)
        }
        assertEquals(TUTORIAL_PLAYER_ORDER.take(4), player.hand.map { it.baseId })

        val aiPlays = ArrayDeque(TUTORIAL_AI_ORDER)
        var firstPlayerTurn = true
        var won = false

        for (step in TUTORIAL_STEPS) when (val e = step.expect) {
            is TutExpect.Mulligan -> {
                // Výměna: náhrada shora, vrácená karta dospod (bez míchání)
                val out = player.hand.first { it.baseId == e.baseId }
                val slot = player.hand.indexOf(out)
                player.hand.remove(out)
                player.drawCards(1)
                player.hand.add(slot, player.hand.removeAt(player.hand.lastIndex))
                player.deck.add(out)
                assertEquals("001", player.hand[slot].baseId)
                // První tah hráče: jen suroviny, bez líznutí
                player.generateResources()
                firstPlayerTurn = false
            }
            is TutExpect.Play -> {
                play(player, ai, e.baseId)
                if (ai.castleHP <= 0) won = true
            }
            is TutExpect.Discard -> {
                val card = player.hand.first { it.baseId == e.baseId }
                player.hand.remove(card)
                player.discardPile.add(card)
                applyEffects(card.discardEffects, player, ai, allCards)
            }
            TutExpect.Opponent -> {
                // Tah soupeře: suroviny, líz, karty ze skriptu (kombo pokračuje další kartou)
                ai.generateResources()
                ai.drawCards(1)
                do { val combo = play(ai, player, aiPlays.removeFirst()) } while (combo && aiPlays.isNotEmpty())
                assertTrue("Hráč nesmí v tutoriálu prohrát", player.castleHP > 0)
                // Začátek dalšího tahu hráče
                player.generateResources()
                player.drawCards(1)
            }
            TutExpect.Info -> Unit
        }

        assertTrue(!firstPlayerTurn)
        assertTrue("Skript má skončit zničením soupeřova hradu, zbývá ${ai.castleHP}", won)
        assertTrue("Soupeř má zahrát všechny karty skriptu, zbylo $aiPlays", aiPlays.isEmpty())
    }
}
