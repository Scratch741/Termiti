package com.example.termiti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karty, jejichž zahození škodí (Pohlcení hradeb: zahození = hrad −8, Poslední výpad /
 * Podkopané valy: hradby −4/−5), AI se plnou rukou nezbavuje zahozením. Čekání ji stojí
 * jen líznutou kartu, která shoří, zahození kartu I hrad nebo hradby – viz AiEngine.bestDiscard.
 */
class AiHarmfulDiscardTest {

    private val allCards: List<Card> by lazy {
        val f = listOf("src/main/assets/cards.json", "app/src/main/assets/cards.json")
            .map(::File).first { it.exists() }
        CardRepository.parseCardsJson(f.readText())
    }

    private fun card(id: String, n: Int = 0) = allCards.first { it.id == id }.copy(id = "${id}_$n")

    /** Plná ruka (7), na nic nejsou suroviny, balíček má karty → AI musí zahodit nebo čekat. */
    private fun ai(wall: Int, vararg hand: Card, res: Map<ResourceType, Int> = emptyMap()) =
        PlayerState(castleHP = 34, wallHP = wall).also {
        ResourceType.entries.forEach { t -> it.resources[t] = res[t] ?: 0 }
        it.hand.addAll(hand)
        repeat(10) { i -> it.deck.add(card("001", 100 + i)) }
    }

    /** Běžné karty bez efektu při zahození. */
    private fun fillers() = listOf("Protiútok", "Vojenská základna", "Pevnost", "Berserk", "Upíří drak", "Krvavý úder")
        .mapIndexed { i, name -> allCards.first { it.name == name }.let { c -> c.copy(id = "${c.id}_$i") } }

    private val opponent get() = PlayerState(castleHP = 30, wallHP = 10).also {
        repeat(10) { i -> it.deck.add(card("001", 200 + i)) }
    }

    @Test
    fun neverDiscardsAbsorbWallsWhileOtherCardsCanGo() {
        // Náhodné plné ruce s Pohlcením hradeb a málo surovinami. Bez hradeb má Pohlcení teď
        // nulovou hodnotu, takže dřív v části z nich vyšlo jako nejslabší karta k zahození.
        val pool = allCards.filter { !it.isPlaceholder && !it.isXCost && it.discardEffects.isEmpty() && it.effects.isNotEmpty() }
        val rnd = java.util.Random(7)
        var discardedSomething = 0
        repeat(3000) { n ->
            val pohlceni = card("139", n)
            val others = (0 until 6).map { i -> pool[rnd.nextInt(pool.size)].let { it.copy(id = "${it.id}_${n}_$i") } }
            val res = mapOf(ResourceType.MAGIC to rnd.nextInt(5), ResourceType.ATTACK to rnd.nextInt(5), ResourceType.STONES to rnd.nextInt(5))
            val action = aiChooseAction(ai(0, pohlceni, *others.toTypedArray(), res = res), opponent)
            if (action is AiAction.Discard) discardedSomething++
            assertFalse("AI zahodila Pohlcení hradeb (ruka ${others.map { it.name }}): $action",
                action is AiAction.Discard && action.card.id == pohlceni.id)
        }
        assertTrue("v testu se vůbec nezahazovalo", discardedSomething > 100)
    }

    @Test
    fun waitsInsteadOfDiscardingWhenEveryCardHurts() {
        // Ruka jen z karet, jejichž zahození škodí → radši čekat (shoří líznutá karta).
        val hand = (0 until 7).map { i -> card(if (i % 2 == 0) "139" else "136", i) }
        val action = aiChooseAction(ai(20, *hand.toTypedArray()), opponent)
        assertEquals(AiAction.Wait, action)
    }

    @Test
    fun wallLossDoesNotProtectWhenThereIsNoWall() {
        // Poslední výpad: zahození = hradby −4. Bez hradeb nic nestojí → smí se zahodit jako jiné.
        val vypad = card("136")
        assertTrue(vypad.discardEffects.any { it is CardEffect.BuildWall && it.amount < 0 })
        val others = fillers()
        val action = aiChooseAction(ai(0, vypad, *others.toTypedArray()), opponent)
        assertTrue("AI s plnou rukou nezahodila nic: $action", action is AiAction.Discard)
    }

    // ── Zamořená Krysa: bez efektu, ale zahrát se dá (3 chaos) – tak se jí AI zbaví bez škody ──

    @Test
    fun playsRatToClearFullHand() {
        val krysa = card("134")
        assertTrue(krysa.effects.isEmpty() && krysa.discardEffects.isNotEmpty())
        val action = aiChooseAction(
            ai(20, krysa, *fillers().toTypedArray(), res = mapOf(ResourceType.CHAOS to 3)), opponent)
        assertEquals(AiAction.Play(krysa), action)
    }

    @Test
    fun keepsRatWhenItWillBePlayableLater() {
        // Na Krysu teď chaos není, ale důl chaosu ano → počkat a zahrát ji, ne zahodit za hrad i hradby.
        val hand = listOf(card("134")) + (1 until 7).map { card("139", it) }
        val state = ai(20, *hand.toTypedArray()).also { it.mines[ResourceType.CHAOS] = 1 }
        assertEquals(AiAction.Wait, aiChooseAction(state, opponent))
    }

    @Test
    fun discardsRatWhenItCanNeverBePaid() {
        // Bez chaosu i bez dolu chaosu by Krysa v ruce zůstala natrvalo → zahodit ji smí.
        val krysa = card("134")
        val hand = listOf(krysa) + (1 until 7).map { card("139", it) }
        val state = ai(20, *hand.toTypedArray()).also { it.mines[ResourceType.CHAOS] = 0 }
        assertEquals(AiAction.Discard(krysa), aiChooseAction(state, opponent))
    }
}
