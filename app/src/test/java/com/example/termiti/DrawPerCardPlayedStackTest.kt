package com.example.termiti

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Inspirace (líz za kartu MAGIE) a Archmág (líz za libovolnou kartu) se v jednom tahu
 * sčítají, nepřepisují – každý aktivní efekt líže zvlášť.
 */
class DrawPerCardPlayedStackTest {

    private val inspirace = listOf(CardEffect.DrawPerCardPlayed("Magie"))
    private val archmag   = listOf(CardEffect.DrawPerCardPlayed(null),
                                   CardEffect.GainResourcePerCardPlayed(ResourceType.MAGIC, 1))

    /** Kolik karet se lízne za zahranou kartu daného typu – stejné pravidlo jako v GameViewModel. */
    private fun drawsFor(state: PlayerState, cardType: String) =
        state.drawCardOnPlay.count { it.isEmpty() || it == cardType }

    private fun play(state: PlayerState, effects: List<CardEffect>) =
        applyEffects(effects, state, PlayerState(), emptyList())

    @Test
    fun inspirationThenArchmageBothDraw() {
        val s = PlayerState()
        play(s, inspirace); play(s, archmag)
        assertEquals(2, drawsFor(s, "Magie"))
        assertEquals(1, drawsFor(s, "Útok"))
    }

    @Test
    fun archmageThenInspirationKeepsArchmageDraw() {
        val s = PlayerState()
        play(s, archmag); play(s, inspirace)
        assertEquals(2, drawsFor(s, "Magie"))
        assertEquals(1, drawsFor(s, "Stavba"))   // dřív 0 – Inspirace Archmága přepsala
    }

    @Test
    fun twoInspirationsDrawTwice() {
        val s = PlayerState()
        play(s, inspirace); play(s, inspirace)
        assertEquals(2, drawsFor(s, "Magie"))
        assertEquals(0, drawsFor(s, "Útok"))
    }

    @Test
    fun deepCopyDoesNotShareTheList() {
        val s = PlayerState()
        play(s, inspirace)
        val copy = s.deepCopy()
        play(s, archmag)
        assertEquals(1, copy.drawCardOnPlay.size)
    }
}
