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

    // Výherní karta: 200, ale mezi více výherními má přednost levnější a kombo (zahraje se a
    // tah pokračuje) – dřív se při shodě vzala první v pořadí karet, např. Posila hradu
    // (2, hrad +5) místo Záplaty (1, hrad +3, kombo), když obě dostavěly hrad na cíl.
    val winScore = 200.0 - (card.cost + card.costModifier).coerceAtLeast(0) * 0.1 + (if (card.isCombo) 0.05 else 0.0)

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
    if (castleDmg >= oppHpLeft) return winScore

    // Dostaví karta hrad rovnou na vítěznou výšku?
    val buildTotal = effects.sumOf { fx ->
        when (fx) {
            is CardEffect.BuildCastle        -> fx.amount.toDouble()
            is CardEffect.ConvertWallToCastle -> self.wallHP.toDouble()
            is CardEffect.XScaledBuildCastle  -> xValue / fx.divisor
            else                              -> 0.0
        }
    }
    if (buildTotal >= selfHpMissing) return winScore

    // ── Váhy: body za jeden bod efektu, všechno ve stejném měřítku ────────────
    // Dřív se hrad dělil tím, kolik chybí do cíle (při cíli 100 a hradu 50 měl bod hradu
    // hodnotu 0,2, bod hradby 1,5) a poškození tím, kolik má soupeř hradu – v constructed
    // tak žolík nabízel hradby místo hradu a krádež surovin místo útoku.
    //  • bod vlastního hradu   2,0 + podíl cesty k cíli (blíž výhře = cennější), × nebezpečí
    //  • bod poškození hradu   2,0 + podíl cesty k jeho zboření
    //  • bod vlastních hradeb  1,5 × nebezpečí;  bod zbořené soupeřovy hradby 1,5
    //  • surovina ~1, krádež 1,5 (mně + jemu −), odčerpání 0,8
    // Poměr hrad : hradby ≈ 1,3–1,9 odpovídá kartám (za 3 kameny hradby +9, nebo hrad +7).
    val danger   = 1.0 + (30 - self.castleHP).coerceAtLeast(0) / 30.0
    val progress = (self.castleHP.toDouble() / selfWinTarget).coerceIn(0.0, 1.0)
    val buildPt  = (2.0 + progress) * danger
    val dmgPt    = 2.0 + (1.0 - oppHpLeft.toDouble() / selfWinTarget).coerceIn(0.0, 1.0)
    val wallPt   = 1.5 * danger
    val oppWallPt = 1.5

    var wallNow = oppWall   // soupeřovy hradby během karty (útok na hradby je sníží dřív než útok)
    for (fx in effects) {
        score += when (fx) {
            is CardEffect.AttackPlayer  -> {
                // Zeď absorbuje útok první; přebytek poškodí hrad
                val absorbed = minOf(fx.amount, wallNow)
                val through  = fx.amount - absorbed
                wallNow -= absorbed
                through * dmgPt + absorbed * oppWallPt
            }
            is CardEffect.AttackCastle  -> fx.amount * dmgPt   // přímo na hrad, přes hradby
            is CardEffect.XScaledAttackPlayer -> {
                val dmg      = xValue / fx.divisor
                val absorbed = minOf(dmg, wallNow.toDouble())
                wallNow = (wallNow - absorbed).toInt()
                (dmg - absorbed) * dmgPt + absorbed * oppWallPt
            }
            is CardEffect.XScaledAttackCastle -> xValue / fx.divisor * dmgPt
            is CardEffect.XScaledBuildCastle  -> xValue / fx.divisor * buildPt
            // Jen hradby, které soupeř opravdu má (Velké zemětřesení bez soupeřových hradeb nic neboří)
            is CardEffect.AttackWall    -> {
                val hit = minOf(fx.amount, wallNow)
                wallNow -= hit
                hit * oppWallPt
            }
            is CardEffect.BuildCastle   -> fx.amount * buildPt
            // Hradby se mění na hrad: hrad získám, hradby ztratím
            is CardEffect.ConvertWallToCastle -> self.wallHP * (buildPt - wallPt)
            // Ztráta vlastních hradeb (Velké zemětřesení: hradby −40) – jen těch, které hráč má
            is CardEffect.BuildWall     -> if (fx.amount < 0) -minOf(-fx.amount, self.wallHP) * wallPt else {
                // Zeď nad strop (self.maxWall) nemá hodnotu – počítej jen to, co se vejde
                val effective = fx.amount.coerceAtMost(self.maxWall - self.wallHP).coerceAtLeast(0)
                effective * wallPt
            }
            is CardEffect.AddMine       -> {
                val mine = (self.mines[fx.type] ?: 1).coerceAtLeast(1)
                20.0 / mine
            }
            is CardEffect.AddResource   -> {
                val res = (self.resources[fx.type] ?: 0)
                fx.amount * (if (res < 3) 1.5 else 1.0)
            }
            is CardEffect.StealResource -> fx.amount * 1.5
            is CardEffect.DrainResource -> fx.amount * 0.8
            is CardEffect.DrawCard      -> fx.count * 8.0
            // Krádež hradu = poškození soupeře + stejně hradu mně
            is CardEffect.StealCastle   -> fx.amount * (dmgPt + buildPt)
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
