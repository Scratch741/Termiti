package com.example.termiti

/**
 * Herní AI pro offline mód — heuristické skórování karet.
 *
 * [aiChooseAction] je čistá funkce (závisí pouze na předaných parametrech),
 * nedrží žádný stav — lze volat z libovolného ViewModel nebo testů.
 */

sealed class AiAction {
    data class Play(val card: Card)    : AiAction()
    data class Discard(val card: Card) : AiAction()
    object Wait                        : AiAction()
}

/**
 * Vybere akci AI podle situace na hracím poli.
 *
 * Priority a logika:
 *  1. ENDGAME (oba balíčky prázdné):
 *     – AI NIKDY neodhazuje karty (zahazování blokuje game-over podmínku).
 *     – Zahraj kartu pokud je výhodná, jinak ČEKEJ.
 *  2. Nemohu si dovolit žádnou kartu:
 *     – Plná ruka + balíček má karty → zahoď nejhorší kartu (jinak příští líznutí spálí kartu).
 *     – Volné místo v ruce + balíček má karty → ČEKEJ (příští tah lízneš potenciálně lepší kartu).
 *     – Balíček prázdný → zahoď nejméně hodnotnou kartu.
 *  3. Normální hra → vyber kartu s nejvyšším skóre.
 *     – Nejlepší karta má ≤0 skóre + plná ruka → zahoď nejhorší kartu.
 *     – Nejlepší karta má ≤0 skóre + ruka není plná → ČEKEJ.
 *
 * Skórování:
 *  – Efekty se hodnotí situačně (nízké HP, blízkost výhry, atd.) + škálují dle amount.
 *  – Podmínkový efekt přidá skóre vnitřního efektu JEN tehdy, když podmínka platí.
 *  – Skóre = součet efektů − cena karty + náhoda ±2.
 *
 * Chytrý výběr karty k zahození (bestDiscard):
 *  – Preferuje zahazovat karty s největším „shortfallem" (daleko od dovolení)
 *    v poměru k rychlosti přírůstku daného zdroje (důl).
 */
fun aiChooseAction(
    ai: PlayerState,
    opponent: PlayerState,
    aiWinTarget: Int = 70,
    playerWinTarget: Int = 70
): AiAction {
    // X-kost karty jsou vždy zahratelné (spotřebují všechen dostupný zdroj, i 0)
    val affordable     = ai.hand.filter { card ->
        if (card.isXCost) true
        else (ai.resources[card.costType] ?: 0) >= card.effectiveCost
    }

    // Situační příznaky
    val aiLowHp        = ai.castleHP < 15
    val aiLowWall      = ai.wallHP   < 5
    val oppLowHp       = opponent.castleHP < 20
    val oppCloseToWin  = opponent.castleHP >= (playerWinTarget - 20) // soupeř je blízko výhry hradem
    val aiCloseToWin   = ai.castleHP       >= (aiWinTarget - 20)    // AI je blízko výhry hradem
    val chaos          = ai.resources[ResourceType.CHAOS] ?: 0
    val bothDecksEmpty = ai.deck.isEmpty() && opponent.deck.isEmpty()
    val handFull       = ai.hand.size >= 7

    // TOTO KOLO buffery – aktivní pokud AI již zahrála TOTO KOLO kartu tento tah
    val buffDrawActive     = ai.drawCardOnPlay != null
    val buffResourceActive = ai.gainResourcePerCardPlayed.isNotEmpty()
    val buffCastleActive   = ai.gainCastlePerCardPlayed.isNotEmpty()
    val anyBuffActive      = buffDrawActive || buffResourceActive || buffCastleActive

    // Počet combo karet v ruce (bez aktuálně hodnocené karty)
    val comboCardsInHand   = ai.hand.count { it.isCombo }

    // Rekurzivní ohodnocení jednoho efektu v kontextu stavu AI
    // xVal = hodnota X pro X-kost efekty (aktuální zásoby daného zdroje)
    // Situační příznaky soupeře – pro chytřejší hodnocení efektů
    val oppWall       = opponent.wallHP
    val oppHasWall    = oppWall > 0
    val oppResources  = opponent.resources

    fun scoreEffect(fx: CardEffect, xVal: Int = 0): Int = when (fx) {
        is CardEffect.AttackPlayer -> {
            // Pokud soupeř nemá hradby, útok jde přímo na hrad → vyšší hodnota
            val wallBonus = if (!oppHasWall) 4 else 0
            val urgency   = if (oppLowHp) 20 else if (oppCloseToWin) 6 else 8
            urgency + fx.amount / 4 + wallBonus
        }
        is CardEffect.AttackCastle -> {
            val urgency = if (oppLowHp) 22 else if (oppCloseToWin) 8 else 6
            urgency + fx.amount / 5
        }
        is CardEffect.AttackWall -> when {
            // Soupeř nemá hradby – karta je k ničemu, silně penalizuj
            !oppHasWall           -> -15
            // Málo hradeb – nízká hodnota, ale nenulová (alespoň trochu poškodíme)
            oppWall < fx.amount   -> 2 + oppWall / 4
            // Hodně hradeb – standardní nebo vyšší hodnota
            else                  -> 4 + oppWall / 6
        }
        // Záporný amount = poškození vlastního hradu (penalta)
        is CardEffect.BuildCastle    -> if (fx.amount >= 0) {
            val urgency = if (aiLowHp) 20 else if (aiCloseToWin) 12 else 5
            urgency + fx.amount / 5
        } else {
            fx.amount * 2  // záporné skóre za ztrátu HP hradu
        }
        // Záporný amount = obětování vlastních hradeb (penalta)
        is CardEffect.BuildWall      -> if (fx.amount >= 0) {
            if (aiLowWall) 16 else 5
        } else {
            fx.amount  // záporné skóre za ztrátu hradeb
        }
        is CardEffect.AddMine             -> 9   // long-term value
        is CardEffect.StealResource       -> {
            // Krást má smysl jen pokud soupeř daný zdroj má
            val available = oppResources[fx.type] ?: 0
            if (available == 0) -5 else 7 + available.coerceAtMost(4)
        }
        is CardEffect.DrainResource       -> {
            // Drainovat má smysl jen pokud soupeř daný zdroj má
            val available = oppResources[fx.type] ?: 0
            if (available == 0) -5 else 6 + available.coerceAtMost(3)
        }
        is CardEffect.AddResource         -> 3
        is CardEffect.AddResourceDelayed  -> fx.amount * 2  // méně než ihned (3), ale hodnotné
        is CardEffect.DestroyMine         -> {
            // Zničit důl má smysl jen pokud soupeř má více než povinné minimum
            val oppMines = opponent.mines[fx.type] ?: 0
            val minMines = if (fx.type == ResourceType.CHAOS) 0 else 1
            if (oppMines <= minMines) -8   // pod minimem – efekt se nevyvolá, zbytečné
            else 8 + (oppMines - minMines) * 2   // čím víc dolů, tím hodnotnější zničení
        }
        is CardEffect.BlockMine           -> {
            // Blokovat důl dává smysl jen pokud soupeř ten důl má
            val oppMines = opponent.mines[fx.type] ?: 0
            if (oppMines == 0) -6 else fx.turns * 7   // 2 kola = 14, 3 kola = 21
        }
        is CardEffect.StealCard           -> {
            // Krást kartu má smysl jen pokud ji skutečně dostaneme do ruky
            val slotsLeft = (7 - ai.hand.size).coerceAtLeast(0)
            if (slotsLeft == 0) -4 else 8   // plná ruka → ukradená karta jen shoří
        }
        is CardEffect.BurnCard            -> if (opponent.hand.isEmpty()) -8 else 6
        is CardEffect.AddCardsToDeck      -> 4
        is CardEffect.DrawCard            -> {
            // Líznout kartu má smysl jen pokud je v ruce místo;
            // karty navíc se spálí → penalizuj každou spálenou kartu
            val slotsLeft   = (7 - ai.hand.size).coerceAtLeast(0)
            val useful      = minOf(fx.count, slotsLeft)
            val burned      = fx.count - useful
            useful * 5 - burned * 4
        }
        // Krádež hradu: poškodí soupeře A léčí vlastní hrad
        is CardEffect.StealCastle    -> fx.amount + (if (oppLowHp) 8 else 0) + (if (aiLowHp) 8 else 0)
        // Podmínkový efekt: skóruj vnitřní efekt pouze pokud podmínka platí; jinak 0
        is CardEffect.ConditionalEffect ->
            if (checkCondition(fx.condition, ai, opponent)) scoreEffect(fx.effect, xVal) else 0

        // X-kost efekty: skóruj proporcionálně k reálné hodnotě efektu
        // amt*2 = lineární bonus (stejná efektivita bez ohledu na výši xVal)
        is CardEffect.XScaledAttackPlayer -> {
            val amt       = xVal / fx.divisor
            val wallBonus = if (!oppHasWall) 4 else 0
            val urgency   = if (oppLowHp) 20 else if (oppCloseToWin) 6 else 8
            urgency + amt * 2 + wallBonus
        }
        is CardEffect.XScaledAttackCastle -> {
            val amt = xVal / fx.divisor
            val urgency = if (oppLowHp) 22 else if (oppCloseToWin) 8 else 7
            urgency + amt * 2
        }
        is CardEffect.XScaledBuildCastle -> {
            val amt = xVal / fx.divisor
            val urgency = if (aiLowHp) 20 else if (aiCloseToWin) 12 else 5
            urgency + amt * 2
        }
        is CardEffect.XScaledDualResource -> {
            val amt = xVal / fx.divisor
            5 + amt * 2   // přidává dva zdroje najednou
        }
        // Výměna rukou: hodnotná, pokud soupeř má víc karet než AI
        is CardEffect.SwapHands ->
            if (opponent.hand.size > ai.hand.size) 10 + (opponent.hand.size - ai.hand.size) * 2 else 3
        // Každá combo karta zahraná po této přinese líz – hodnotnější pokud AI má víc combo karet v ruce
        is CardEffect.DrawPerCardPlayed -> {
            // Buff stojí za to jen pokud máme combo karty k zahrání
            if (comboCardsInHand == 0) -2 else 5 + comboCardsInHand * 4
            // Poznámka: dostupnost zdrojů po zaplacení se kontroluje v score() níže
        }
        // Každá zahraná karta přidá zdroje – hodnotnější při více combo kartách v ruce
        is CardEffect.GainResourcePerCardPlayed -> {
            if (comboCardsInHand == 0) -2 else 4 + comboCardsInHand * (fx.amount + 1)
        }
        // Každá zahraná stavební karta přidá HP hradu – hodnotnější při více combo v ruce
        is CardEffect.GainCastlePerCardPlayed -> {
            if (comboCardsInHand == 0) -2 else 4 + comboCardsInHand * (fx.amount + 1)
        }
        // Wildcard – průměrná hodnota náhodné karty
        is CardEffect.ShapeShift -> 5
        // Rozhodnutí – statické skóre (AI auto-vybírá první možnost)
        is CardEffect.DecisionBurnOpponent  -> 6
        is CardEffect.DecisionChooseType    -> 5
        is CardEffect.DecisionFromDiscard   -> 4
        is CardEffect.DecisionFromDeck      -> 5
        // Konverze vlastního dolu: hodnotné pokud AI má chaos strategii
        is CardEffect.ConvertMine -> {
            val chaosMin = ai.mines[ResourceType.CHAOS] ?: 0
            if (fx.from == ResourceType.MAGIC && (ai.mines[fx.from] ?: 0) > 1) 4 + chaosMin else 0
        }
        // Líz pro oba hráče: hodnotný jen pokud AI má místo v ruce; soupeřův líz penalizujeme
        is CardEffect.DrawBoth -> {
            val slotsLeft = (7 - ai.hand.size).coerceAtLeast(0)
            val useful    = minOf(fx.count, slotsLeft)
            val burned    = fx.count - useful
            useful * 5 - burned * 4 - fx.count * 2   // -2 za každou soupeřovu líznutou kartu
        }
    }

    // ── Detekce lethal: karta okamžitě vyhraje hru tento tah ──────────────
    // Simulujeme poškození hradeb + hradu (soupeřův hrad → 0) NEBO
    // nárůst vlastního hradu na win target (výhra postavením hradu).
    fun isLethal(card: Card, xVal: Int): Boolean {
        var wallLeft       = opponent.wallHP
        var castleDmg      = 0
        var selfCastleGain = 0
        fun processEffect(fx: CardEffect) {
            when (fx) {
                is CardEffect.AttackPlayer -> {
                    val pierce   = (fx.amount - wallLeft).coerceAtLeast(0)
                    wallLeft     = (wallLeft - fx.amount).coerceAtLeast(0)
                    castleDmg   += pierce
                }
                is CardEffect.AttackCastle        -> castleDmg += fx.amount
                is CardEffect.XScaledAttackPlayer -> {
                    val amt      = xVal / fx.divisor
                    val pierce   = (amt - wallLeft).coerceAtLeast(0)
                    wallLeft     = (wallLeft - amt).coerceAtLeast(0)
                    castleDmg   += pierce
                }
                is CardEffect.XScaledAttackCastle -> castleDmg += xVal / fx.divisor
                is CardEffect.StealCastle         -> {
                    castleDmg      += fx.amount
                    selfCastleGain += fx.amount
                }
                is CardEffect.BuildCastle         -> if (fx.amount > 0) selfCastleGain += fx.amount
                is CardEffect.XScaledBuildCastle  -> selfCastleGain += xVal / fx.divisor
                is CardEffect.ConditionalEffect   ->
                    if (checkCondition(fx.condition, ai, opponent)) processEffect(fx.effect)
                else -> {}
            }
        }
        card.effects.forEach { processEffect(it) }
        // Výhra zničením soupeřova hradu NEBO dosažením win targetu vlastním hradem
        return castleDmg >= opponent.castleHP ||
               (ai.castleHP + selfCastleGain) >= aiWinTarget
    }

    // Celkové skóre karty = suma efektů − cena + šum ±2
    // Pro X-kost karty: cena = aktuální zásoby daného zdroje (to se spotřebuje)
    fun score(card: Card): Int {
        val xVal = if (card.isXCost) (ai.resources[card.costType] ?: 0) else 0

        // ── Lethal override: karta okamžitě vyhrává hru → vždy zahraj ────
        if (isLethal(card, xVal)) return 1000 + (-2..2).random()

        // X-kost karty: nehrát příliš brzy – malé zásoby = mizivý efekt, plýtvání kartou.
        // Práh závisí na situaci; při prázdných balíčcích zásoby stejně vyprší → hraj vždy.
        if (card.isXCost && !bothDecksEmpty) {
            val minX = when {
                aiLowHp && oppLowHp -> 2   // oba v nouzi – zahraj i za málo
                aiLowHp || oppLowHp -> 4   // jeden v nebezpečí – nižší práh
                else                -> 8   // normální hra – čekej na solidní zásoby
            }
            if (xVal < minX) return -20 + (-2..2).random()
        }

        // Ochrana před sebevraždou: pokud karta sníží vlastní hrad na ≤0,
        // zahraj ji pouze v situaci jisté prohry (šance na remízu).
        // Sebevraždu detekujeme jako záporné BuildCastle efekty.
        fun selfCastleDamage(effects: List<CardEffect>): Int = effects.sumOf { fx ->
            when {
                fx is CardEffect.BuildCastle && fx.amount < 0 -> -fx.amount
                fx is CardEffect.ConditionalEffect            ->
                    if (checkCondition(fx.condition, ai)) selfCastleDamage(listOf(fx.effect)) else 0
                else                                          -> 0
            }
        }
        val selfDmg = selfCastleDamage(card.effects)
        if (selfDmg > 0 && selfDmg >= ai.castleHP) {
            // Karta by zničila vlastní hrad – povolíme pouze pro remízu:
            // AI je v jisté ztrátě = soupeřův hrad je nízký (mohl by příštím tahem vyhrát)
            // NEBO AI nemá žádnou šanci na obnovu a soupeř je téměř na výhře
            val couldTieKill = opponent.castleHP <= selfDmg  // i soupeř by zahynul (remíza)
            val certainLoss  = ai.castleHP <= 10 && (oppCloseToWin || opponent.castleHP < 15)
            if (!couldTieKill && !certainLoss) return -100 + (-2..2).random()
        }

        val effectScore = card.effects.sumOf { scoreEffect(it, xVal) }
        val costForScore = if (card.isXCost) xVal else card.effectiveCost
        val chaosBlock  = if (card.costType == ResourceType.CHAOS && chaos < card.effectiveCost) 100 else 0
        val noise       = (-2..2).random()

        // ── TOTO KOLO penalty: zahraj jen pokud zbydou resources na combo kartu ──
        // Zkontroluj, zda AI po zaplacení této TOTO KOLO karty může zahrát
        // alespoň jednu combo kartu z ruky. Pokud ne, buff by vyšel naprázdno.
        val isTotoKolo = card.effects.any {
            it is CardEffect.DrawPerCardPlayed ||
            it is CardEffect.GainResourcePerCardPlayed ||
            it is CardEffect.GainCastlePerCardPlayed
        }
        val totoKoloPenalty = if (isTotoKolo && comboCardsInHand > 0) {
            val residualRes = ai.resources.toMutableMap()
            residualRes[card.costType] = ((residualRes[card.costType] ?: 0) - card.effectiveCost).coerceAtLeast(0)
            val canAffordCombo = ai.hand.any { combo ->
                combo.isCombo && combo.id != card.id &&
                (residualRes[combo.costType] ?: 0) >= combo.effectiveCost
            }
            if (!canAffordCombo) -25 else 0  // po zaplacení není na žádnou combo → silná penalta
        } else 0

        // ── TOTO KOLO bonus pro combo karty ───────────────────────────────────
        // Pokud je aktivní buff z TOTO KOLO karty, combo karty dostávají velký bonus.
        val totoBuff = if (anyBuffActive && card.isCombo) {
            val activeBuffCount = listOf(buffDrawActive, buffResourceActive, buffCastleActive).count { it }
            val drawBonus = if (buffDrawActive) 4 else 0
            activeBuffCount * 6 + drawBonus
        } else 0

        return effectScore - costForScore - chaosBlock + totoKoloPenalty + totoBuff + noise
    }

    // Chytrý výběr karty k zahození:
    // Preferuje zahazovat karty s největším shortfallem / rychlostí dolu
    // (= karty, na které bychom čekali nejdéle)
    fun bestDiscard(): Card? = ai.hand.maxByOrNull { card ->
        val shortfall = (card.effectiveCost - (ai.resources[card.costType] ?: 0)).coerceAtLeast(0)
        val mineRate  = (ai.mines[card.costType] ?: 0).coerceAtLeast(1)
        shortfall * 10 / mineRate
    }

    // =====================================================================
    // ENDGAME: oba balíčky prázdné
    // =====================================================================
    // AI NESMÍ odhazovat – ztratila by tah a podmínka „oba čekají s prázdnými balíčky"
    // (= resolveByHp) by se nikdy nevyvolala. Vždy zahraj nebo ČEKEJ.
    if (bothDecksEmpty) {
        val aiIsLosing = ai.castleHP < opponent.castleHP
        if (affordable.isNotEmpty()) {
            val scored = affordable.map { it to score(it) }
            val (best, bestScore) = scored.maxByOrNull { it.second } ?: return AiAction.Wait
            if (bestScore > 0) return AiAction.Play(best)

            // AI prohrává a nemá výhodnou kartu → poslední pokus:
            // zahraj cokoli, co útočí nebo staví hrad (čekání = jistá prohra)
            if (aiIsLosing) {
                val lastChance = affordable.firstOrNull { card ->
                    card.effects.any { fx ->
                        fx is CardEffect.AttackPlayer ||
                        fx is CardEffect.AttackCastle ||
                        fx is CardEffect.BuildCastle  ||
                        fx is CardEffect.StealCastle
                    }
                }
                if (lastChance != null) return AiAction.Play(lastChance)
            }
        }
        return AiAction.Wait
    }

    // =====================================================================
    // NEMOHU SI DOVOLIT ŽÁDNOU KARTU
    // =====================================================================
    if (affordable.isEmpty()) {
        // Plná ruka + balíček má karty → zahoď nejhorší kartu
        // (čekání by způsobilo spálení líznuté karty, protože ruka je plná)
        if (handFull && ai.deck.isNotEmpty()) {
            return bestDiscard()?.let { AiAction.Discard(it) } ?: AiAction.Wait
        }
        // Volné místo v ruce + balíček má karty → čekej, příště lízneš novou kartu
        if (ai.deck.isNotEmpty()) return AiAction.Wait
        // Balíček prázdný → zahoď nejméně hodnotnou kartu
        return bestDiscard()?.let { AiAction.Discard(it) } ?: AiAction.Wait
    }

    // =====================================================================
    // NORMÁLNÍ HRA – vyber nejlepší kartu
    // =====================================================================
    // Předpočítej skóre jednou (score() obsahuje náhodu, nevolej dvakrát)
    val scored = affordable.map { it to score(it) }
    val (best, bestScore) = scored.maxByOrNull { it.second } ?: return AiAction.Wait

    // Pokud je i nejlepší karta nevýhodná (podmínka nesplněna, čisté náklady):
    // – plná ruka → zahoď nejhorší kartu (uvolni místo pro lepší líz)
    // – jinak → čekej
    if (bestScore <= 0) {
        return if (handFull && ai.deck.isNotEmpty()) {
            bestDiscard()?.let { AiAction.Discard(it) } ?: AiAction.Wait
        } else {
            AiAction.Wait
        }
    }

    return AiAction.Play(best)
}
