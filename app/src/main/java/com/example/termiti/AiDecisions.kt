package com.example.termiti

// Rozhodovací karty (Decision*, SmartJoker, PeekAndStealHand): nabídka možností
// a situační hodnocení karty. Používá GameViewModel (hráč i AI) a simulace
// s herním enginem v src/test (EngineSimulation) – proto top-level, ne členy
// GameViewModel, který bez Application nevznikne.

/**
 * Ohodnotí vhodnost karty [card] pro aktuální situaci hráče [self] s cílovým HP [selfWinTarget]
 * soupeřícím proti [opp]. Vyšší skóre = karta se více hodí do situace.
 */
internal fun scoreCardForSituation(
    card: Card,
    self: PlayerState,
    opp: PlayerState,
    selfWinTarget: Int = 70
): Double {
    var score = 0.0
    val selfHpMissing = (selfWinTarget - self.castleHP).coerceAtLeast(1)
    val oppHpLeft     = opp.castleHP.coerceAtLeast(1)

    val oppWall = opp.wallHP.coerceAtLeast(0)
    val xValue  = (self.resources[card.costType] ?: 0).toDouble()

    // Podmínkový efekt se nesmí hodnotit naslepo: když podmínka PRÁVĚ TEĎ platí,
    // rozhoduje jeho vnitřní efekt (Odstřelovač = 5 + dalších 5 na hrad), když ne,
    // karta ten efekt neudělá vůbec. Bez tohohle spadl celý ConditionalEffect do
    // větve `else -> 2.0` a karta, která vyhrává hru, prohrála s obyčejnou osmičkou.
    fun flatten(effects: List<CardEffect>, depth: Int = 0): List<CardEffect> =
        effects.flatMap { fx ->
            if (fx is CardEffect.ConditionalEffect && depth < 3)
                if (checkCondition(fx.condition, self, opp)) flatten(listOf(fx.effect), depth + 1)
                else emptyList()
            else listOf(fx)
        }
    val effects = flatten(card.effects)

    // Vyhrává karta TEĎ? Poškození se sčítá přes VŠECHNY efekty karty, ne po jednom –
    // Dvojitý úder (hrad 7 + hráč 7) i Odstřelovač (5 + podmíněných 5) zabíjí až součtem.
    // Zeď pohltí jen útok na hráče, a to až po případném rozbití zdi na stejné kartě.
    var wallLeft   = oppWall
    var castleDmg  = 0.0
    for (fx in effects) when (fx) {
        is CardEffect.AttackWall          -> wallLeft = (wallLeft - fx.amount).coerceAtLeast(0)
        is CardEffect.AttackCastle        -> castleDmg += fx.amount
        is CardEffect.StealCastle         -> castleDmg += fx.amount
        is CardEffect.XScaledAttackCastle -> castleDmg += xValue / fx.divisor
        is CardEffect.AttackPlayer        -> {
            val through = (fx.amount - wallLeft).coerceAtLeast(0)
            wallLeft = (wallLeft - fx.amount).coerceAtLeast(0)
            castleDmg += through
        }
        is CardEffect.XScaledAttackPlayer -> {
            val dmg     = xValue / fx.divisor
            val through = (dmg - wallLeft).coerceAtLeast(0.0)
            wallLeft = (wallLeft - dmg).coerceAtLeast(0.0).toInt()
            castleDmg += through
        }
        else -> {}
    }
    if (castleDmg >= oppHpLeft) return 200.0

    // Dostaví karta hrad rovnou na vítěznou výšku?
    val buildTotal = effects.sumOf { fx ->
        when (fx) {
            is CardEffect.BuildCastle        -> fx.amount.toDouble()
            is CardEffect.ConvertWallToCastle -> self.wallHP.toDouble()
            is CardEffect.XScaledBuildCastle  -> xValue / fx.divisor
            else                              -> 0.0
        }
    }
    if (buildTotal >= selfHpMissing) return 200.0

    for (fx in effects) {
        score += when (fx) {
            is CardEffect.AttackPlayer  -> {
                // Zeď absorbuje útok první; pouze přebytek poškodí hrad
                val castleDmg = (fx.amount - oppWall).coerceAtLeast(0).toDouble()
                if (castleDmg >= oppHpLeft) 200.0 else castleDmg * 12.0 / oppHpLeft
            }
            is CardEffect.AttackCastle  -> {
                // Přímý útok na hrad (přeskakuje zeď)
                val dmg = fx.amount.toDouble()
                if (dmg >= oppHpLeft) 200.0 else dmg * 12.0 / oppHpLeft
            }
            is CardEffect.XScaledAttackPlayer -> {
                val dmg = xValue / fx.divisor
                val castleDmg = (dmg - oppWall).coerceAtLeast(0.0)
                if (castleDmg >= oppHpLeft) 200.0 else castleDmg * 12.0 / oppHpLeft
            }
            is CardEffect.XScaledAttackCastle -> {
                val dmg = xValue / fx.divisor
                if (dmg >= oppHpLeft) 200.0 else dmg * 12.0 / oppHpLeft
            }
            is CardEffect.XScaledBuildCastle  -> {
                val amt = xValue / fx.divisor
                if (amt >= selfHpMissing) 200.0 else amt * 10.0 / selfHpMissing
            }
            is CardEffect.AttackWall    -> fx.amount * 3.0
            is CardEffect.BuildCastle   -> {
                if (fx.amount >= selfHpMissing) 200.0
                else fx.amount * 10.0 / selfHpMissing
            }
            is CardEffect.ConvertWallToCastle -> {
                val amt = self.wallHP
                if (amt >= selfHpMissing) 200.0 else amt * 10.0 / selfHpMissing
            }
            is CardEffect.BuildWall     -> {
                // Zeď nad cap (self.maxWall) nemá hodnotu – počítej jen to, co se vejde
                val effective = fx.amount.coerceAtMost(self.maxWall - self.wallHP).coerceAtLeast(0)
                // Nízký hrad → obrana je cennější (×1.0 při 30+, až ×2.0 při hradu u nuly)
                val danger = 1.0 + (30 - self.castleHP).coerceAtLeast(0) / 30.0
                effective * 1.5 * danger
            }
            is CardEffect.AddMine       -> {
                val mine = (self.mines[fx.type] ?: 1).coerceAtLeast(1)
                20.0 / mine
            }
            is CardEffect.AddResource   -> {
                val res = (self.resources[fx.type] ?: 0)
                fx.amount * (if (res < 3) 4.0 else 1.5)
            }
            is CardEffect.StealResource -> fx.amount * 5.0
            is CardEffect.DrainResource -> fx.amount * 3.0
            is CardEffect.DrawCard      -> fx.count * 8.0
            is CardEffect.StealCastle   -> fx.amount * 6.0
            is CardEffect.DestroyMine   -> 10.0
            is CardEffect.StealCard     -> 12.0
            is CardEffect.BurnCard      -> 8.0
            else                        -> 2.0
        }
    }
    // Ve fallback (žádná dostupná karta): preferuj tu nejblíže k dostupnosti
    val res           = self.resources[card.costType] ?: 0
    val effectiveCost = (card.cost + card.costModifier).coerceAtLeast(0)
    val deficit       = (effectiveCost - res).coerceAtLeast(0)
    if (deficit > 0) score -= deficit * 5.0   // penalizace za každý chybějící zdroj
    return score
}

internal fun decisionOptions(
    fx: CardEffect,
    self: PlayerState,
    opponent: PlayerState,
    allCards: List<Card>,
    excludeId: String? = null,   // vyloučí právě zahranou kartu z nabídky (Vzpomínka)
    selfWinTarget: Int = 70,
    /** Karta pro volbu suroviny (DecisionChooseResource) – ve hře s artem a lokalizací. */
    resourceCard: (ResourceType, Int) -> Card
): List<Card> = when (fx) {
    is CardEffect.DecisionBurnOpponent -> opponent.deck.filter { !it.isPileJunk }.shuffled().take(fx.picks)
    is CardEffect.DecisionChooseType   -> allCards.filter { it.type == fx.cardType && !it.isPlaceholder }.shuffled().take(fx.picks)
        .map { if (fx.costReduction > 0) it.copy(costModifier = -fx.costReduction) else it }
    is CardEffect.DecisionFromDiscard  -> self.discardPile.filter { it.id != excludeId && !it.isPileJunk }.shuffled().take(fx.picks)
    is CardEffect.DecisionFromDeck     -> self.deck.filter { !it.isPileJunk }.shuffled().take(fx.picks)
    is CardEffect.DecisionDrawFromDeck -> self.deck.filter { !it.isPileJunk }.take(fx.picks)
    is CardEffect.DecisionMine         -> {
        // Vždy 4 možnosti (1 důl od každého typu). Dedup karet s více AddMine
        // efekty (Trifekta dolů, Velkovýroba…) musí probíhat BĚHEM výběru –
        // dodatečný distinctBy by po náhodné kolizi srazil nabídku na 3 karty.
        val seen = mutableSetOf<String>()
        listOf(
            ResourceType.MAGIC, ResourceType.ATTACK, ResourceType.STONES, ResourceType.CHAOS
        ).mapNotNull { resType ->
            allCards.filter { card ->
                card.baseId !in seen && !card.isPlaceholder &&
                    card.effects.any { e -> e is CardEffect.AddMine && e.type == resType }
            }.shuffled().firstOrNull()?.also { seen.add(it.baseId) }
        }
    }
    is CardEffect.SmartJoker           -> listOf("Magie", "Útok", "Stavba", "Chaos").mapNotNull { typeName ->
        // Z každého typu nejdřív filtr na karty, které si hráč může PRÁVĚ dovolit;
        // teprve z nich vybere situačně nejlepší. Jen pokud žádná dostupná není,
        // sáhne na nejdostupnější nedostupnou (jako výhledová volba).
        val pool = allCards.filter { it.type == typeName && !it.isPlaceholder }
        val affordable = pool.filter { card ->
            val res  = self.resources[card.costType] ?: 0
            val cost = (card.cost + card.costModifier).coerceAtLeast(0)
            res >= cost
        }
        val candidates = affordable.ifEmpty { pool }
        candidates.maxByOrNull { scoreCardForSituation(it, self, opponent, selfWinTarget) }
    }
    is CardEffect.PeekAndStealHand      -> opponent.hand.filter { !it.isPileJunk }
    is CardEffect.DecisionChooseResource -> fx.options.map { opt -> resourceCard(opt.type, opt.amount) }
    else -> emptyList()
}
