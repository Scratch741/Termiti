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
    val aiWallRoom     = (MAX_WALL - ai.wallHP).coerceAtLeast(0)  // kolik hradeb ještě lze postavit
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

    // Odpovídá typ [actualType] filtru [filterType]? null/"" = libovolný typ.
    // Používá se pro DrawPerCardPlayed/GainResourcePerCardPlayed/GainCastlePerCardPlayed,
    // jejichž trigger kontroluje card.type (např. "Magie"), NE card.isCombo –
    // combo karta jiného typu buff vůbec nespustí (viz GameViewModel._playCard).
    fun typeMatches(filterType: String?, actualType: String): Boolean =
        filterType.isNullOrEmpty() || filterType == actualType

    // Počet karet v ruce (bez karty [excludeId]) odpovídajících typovému filtru.
    fun matchingTypeCount(cardType: String?, excludeId: String?): Int =
        ai.hand.count { it.id != excludeId && typeMatches(cardType, it.type) }

    // Typové filtry všech TOTO KOLO efektů dané karty (null = libovolný typ).
    fun totoFiltersOf(c: Card): List<String?> {
        val filters = mutableListOf<String?>()
        c.effects.forEach { fx ->
            when (fx) {
                is CardEffect.DrawPerCardPlayed         -> filters.add(fx.cardType)
                is CardEffect.GainResourcePerCardPlayed -> filters.add(fx.cardType)
                is CardEffect.GainCastlePerCardPlayed   -> filters.add(fx.cardType)
                else -> {}
            }
        }
        return filters
    }

    // Rekurzivní ohodnocení jednoho efektu v kontextu stavu AI
    // xVal = hodnota X pro X-kost efekty (aktuální zásoby daného zdroje)
    // ownerId = id karty, které efekt patří (vyloučena z matchingTypeCount, aby se nepočítala sama)
    // Situační příznaky soupeře – pro chytřejší hodnocení efektů
    val oppWall       = opponent.wallHP
    val oppHasWall    = oppWall > 0
    val oppResources  = opponent.resources

    fun scoreEffect(fx: CardEffect, xVal: Int = 0, ownerId: String? = null): Int = when (fx) {
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
            val effectiveGain = fx.amount.coerceAtMost(aiWallRoom)
            when {
                effectiveGain == 0 -> -5           // zeď je plná, efekt přijde vniveč
                aiLowWall          -> 16            // urgentní obrana
                effectiveGain < fx.amount -> 2 + effectiveGain / 3  // blízko capu – jen částečný zisk
                else               -> 5            // plná hodnota
            }
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
        is CardEffect.AddToOpponentDeck   -> fx.count * 5  // 3× Bomba = potenciál 15 dmg na hrad
        is CardEffect.TrapOnDraw          -> 0  // pasca se nehraje přímo
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
        is CardEffect.RandomizeHands ->
            if (ai.hand.size < 3 && ai.deck.isNotEmpty()) 12 else if (opponent.hand.size > ai.hand.size) 8 else 2
        is CardEffect.GiveRandomCard -> 6
        is CardEffect.ModifyHandCost ->
            if (fx.targetOpponent) opponent.hand.size * fx.delta * 2
            else ai.hand.size * (-fx.delta) * 2
        // Každá další zahraná karta ODPOVÍDAJÍCÍHO TYPU přinese líz – hodnotnější
        // s víc takovými kartami v ruce (trigger kontroluje card.type, ne isCombo!)
        is CardEffect.DrawPerCardPlayed -> {
            val matches = matchingTypeCount(fx.cardType, ownerId)
            // Buff stojí za to jen pokud máme odpovídající karty k zahrání
            if (matches == 0) -2 else 5 + matches * 4
            // Poznámka: dostupnost zdrojů po zaplacení se kontroluje v score() níže
        }
        // Každá další zahraná karta odpovídajícího typu přidá zdroje
        is CardEffect.GainResourcePerCardPlayed -> {
            val matches = matchingTypeCount(fx.cardType, ownerId)
            if (matches == 0) -2 else 4 + matches * (fx.amount + 1)
        }
        // Každá další zahraná karta odpovídajícího typu přidá HP hradu
        is CardEffect.GainCastlePerCardPlayed -> {
            val matches = matchingTypeCount(fx.cardType, ownerId)
            if (matches == 0) -2 else 4 + matches * (fx.amount + 1)
        }
        // Wildcard – průměrná hodnota náhodné karty
        is CardEffect.ShapeShift -> 5
        // Rozhodnutí – statické skóre (AI auto-vybírá první možnost)
        is CardEffect.DecisionBurnOpponent  -> 6
        is CardEffect.DecisionChooseType    -> 5
        is CardEffect.DecisionFromDiscard   -> 4
        is CardEffect.DecisionFromDeck      -> 5
        is CardEffect.DecisionDrawFromDeck  -> 5
        is CardEffect.DecisionMine          -> 6
        // Konverze vlastního dolu: hodnotné pokud AI má chaos strategii
        is CardEffect.ConvertMine -> {
            val chaosMin  = ai.mines[ResourceType.CHAOS] ?: 0
            val sourceMines = ai.mines[fx.from] ?: 0
            // Efekt nelze snížit pod 1 → hrát lze i s přesně 1 dolem (důl zůstane zachován)
            if (fx.from == ResourceType.MAGIC && sourceMines >= 1) 4 + chaosMin else 0
        }
        // Líz pro oba hráče: hodnotný jen pokud AI má místo v ruce; soupeřův líz penalizujeme
        is CardEffect.DrawBoth -> {
            val slotsLeft = (7 - ai.hand.size).coerceAtLeast(0)
            val useful    = minOf(fx.count, slotsLeft)
            val burned    = fx.count - useful
            useful * 5 - burned * 4 - fx.count * 2
        }
        // Klonování: hodnotné pokud AI má silné karty v ruce (přidá 2 kopie příští zahrané karty)
        is CardEffect.CloneNextPlayed -> 6
        is CardEffect.SmartJoker      -> 8   // Rozhodnutí: silná situační karta
        is CardEffect.MomentumAttack  ->
            fx.base + ai.attackCardsThisTurn * fx.bonusPerAttack
        is CardEffect.PeekAndStealHand ->
            if (opponent.hand.isNotEmpty()) 8 + opponent.hand.size else 2
        is CardEffect.DecisionChooseResource ->
            fx.options.maxOfOrNull { it.amount } ?: 4
        is CardEffect.Mirror -> {
            val src = opponent.lastPlayedCard
            when {
                src == null -> -100  // žádná zdrojová karta → zcela bez efektu, nikdy nehrát
                src.effects.any { it is CardEffect.Mirror || it is CardEffect.Clone } -> 2  // zabránit rekurzi
                else -> src.effects.sumOf { scoreEffect(it) }.coerceIn(2, 20)
            }
        }
        is CardEffect.Clone -> {
            val src = ai.lastPlayedCard
            when {
                src == null -> -100  // žádná zdrojová karta → pouze +2 magie = nevýhodné, nikdy nehrát
                src.effects.any { it is CardEffect.Mirror || it is CardEffect.Clone } -> 2  // zabránit rekurzi
                else -> src.effects.sumOf { scoreEffect(it) }.coerceIn(2, 25)
            }
        }
        is CardEffect.NextCardIsCombo -> 3  // dává příští kartě combo = slabý ale užitečný efekt
        is CardEffect.DiscountRandomCard -> fx.delta * fx.count  // okamžitá sleva na náhodnou kartu
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
            // Karta by zničila vlastní hrad – povolíme pouze pokud jde o remízu
            // (útočné efekty karty zároveň sníží soupeřův hrad na ≤ 0).
            val oppDmg = card.effects.sumOf { fx ->
                when (fx) {
                    is CardEffect.AttackCastle        -> fx.amount
                    is CardEffect.AttackPlayer        -> (fx.amount - opponent.wallHP).coerceAtLeast(0)
                    is CardEffect.XScaledAttackCastle -> xVal / fx.divisor
                    is CardEffect.XScaledAttackPlayer -> ((xVal / fx.divisor) - opponent.wallHP).coerceAtLeast(0)
                    else                              -> 0
                }
            }
            val isActualDraw = opponent.castleHP - oppDmg <= 0
            if (!isActualDraw) return -500 + (-2..2).random()
        }

        val effectScore = card.effects.sumOf { scoreEffect(it, xVal, card.id) }
        val costForScore = if (card.isXCost) xVal else card.effectiveCost
        val chaosBlock  = if (card.costType == ResourceType.CHAOS && chaos < card.effectiveCost) 100 else 0
        val noise       = (-2..2).random()

        // ── TOTO KOLO penalty: zahraj jen pokud zbydou resources na kartu ODPOVÍDAJÍCÍHO TYPU ──
        // Trigger DrawPerCardPlayed/GainResourcePerCardPlayed/GainCastlePerCardPlayed kontroluje
        // typ zahrané karty (card.type, např. "Magie"), NE card.isCombo – payoff karta nemusí
        // být combo (klidně ukončí tah, buff se stejně spustí). Zkontroluj proto typový filtr.
        val totoFilters = totoFiltersOf(card)
        val isTotoKolo  = totoFilters.isNotEmpty()
        val totoKoloPenalty = if (isTotoKolo) {
            val residualRes = ai.resources.toMutableMap()
            residualRes[card.costType] = ((residualRes[card.costType] ?: 0) - card.effectiveCost).coerceAtLeast(0)
            val canAffordPayoff = totoFilters.any { filter ->
                ai.hand.any { other ->
                    other.id != card.id &&
                    typeMatches(filter, other.type) &&
                    (residualRes[other.costType] ?: 0) >= other.effectiveCost
                }
            }
            if (!canAffordPayoff) -25 else 0  // po zaplacení není na žádnou odpovídající kartu → silná penalta
        } else 0

        // ── „Počkej na setup" penalty: v ruce čeká nezahraná TOTO KOLO karta (Inspirace apod.),
        // jejíž filtr odpovídá typu PRÁVĚ hodnocené karty, a AI si ji může dovolit zahrát dřív –
        // zahráním této karty TEĎ by se buff promarnil (ještě není aktivní). Mírně odrazuj,
        // ať AI radši nejdřív odehraje setup kartu.
        val waitForSetupPenalty = if (!anyBuffActive) {
            val pendingSetup = ai.hand.any { other ->
                other.id != card.id &&
                totoFiltersOf(other).any { filter -> typeMatches(filter, card.type) } &&
                (ai.resources[other.costType] ?: 0) >= other.effectiveCost
            }
            if (pendingSetup) -8 else 0
        } else 0

        // ── CloneNextPlayed skóre: zahraj jen pokud po zaplacení zbydou zdroje na další kartu ──
        // CloneNextPlayed (Chaotická replikace atd.) nemá žádný efekt, pokud AI nezahraje
        // po ní alespoň jednu další kartu.
        val hasCloneNextPlayed = card.effects.any { it is CardEffect.CloneNextPlayed }
        val clonePenalty = if (hasCloneNextPlayed) {
            val residualRes = ai.resources.toMutableMap()
            residualRes[card.costType] = ((residualRes[card.costType] ?: 0) - card.effectiveCost).coerceAtLeast(0)
            val followUpCards = ai.hand.filter { other ->
                other.id != card.id && (residualRes[other.costType] ?: 0) >= other.effectiveCost
            }
            when {
                followUpCards.isEmpty() -> -30  // žádná follow-up karta → silná penalta
                // Bonus za hodnotný cíl klonu (nejlepší follow-up karta)
                else -> followUpCards.maxOfOrNull { other ->
                    other.effects.sumOf { scoreEffect(it, 0, other.id) }
                }?.let { bestFollowScore -> (bestFollowScore / 3).coerceIn(0, 12) } ?: 0
            }
        } else 0

        // ── TOTO KOLO bonus pro odpovídající typ ────────────────────────────────
        // Pokud je aktivní buff z TOTO KOLO karty A tato karta odpovídá jeho typovému
        // filtru, dostane bonus (bez ohledu na isCombo – i nekombo karta buff spustí).
        val totoBuff = run {
            var bonus = 0
            if (buffDrawActive && typeMatches(ai.drawCardOnPlay, card.type)) bonus += 10
            if (ai.gainResourcePerCardPlayed.any { typeMatches(it.cardType, card.type) }) bonus += 6
            if (ai.gainCastlePerCardPlayed.any { typeMatches(it.cardType, card.type) }) bonus += 6
            bonus
        }

        return effectScore - costForScore - chaosBlock + totoKoloPenalty + clonePenalty + totoBuff + waitForSetupPenalty + noise
    }

    // Chytrý výběr karty k zahození:
    // Zahodí kartu s nejnižší "hodnotou v ruce":
    //   hodnotaVRuce = síla efektů × 2  −  táhla_do_dovolení × 3
    // Silné karty (Démon, drak…) se drží i za cenu dlouhého čekání.
    // Slabé karty, na které se navíc čeká, jsou první kandidáti na zahození.
    fun bestDiscard(): Card? = ai.hand.minByOrNull { card ->
        val effectScore   = card.effects.sumOf { scoreEffect(it) }.coerceAtLeast(0)
        val shortfall     = (card.effectiveCost - (ai.resources[card.costType] ?: 0)).coerceAtLeast(0)
        val mineRate      = (ai.mines[card.costType] ?: 0).coerceAtLeast(1)
        // Penalizace za nedostupnost je stropována na 4 tahy — drahé late-game karty
        // se nesmí zahazovat jen proto, že jsou teď nedostupné.
        val turnsToAfford = (shortfall.toFloat() / mineRate).coerceAtMost(4f)
        // Malý bonus za cenu: dražší karta = silnější late-game potenciál.
        val costBonus     = card.effectiveCost / 2
        effectScore * 2 - turnsToAfford * 2 + costBonus
    }

    // ── Combo-chain lethal (1 krok dopředu) ───────────────────────────────────
    // Pokud zahrání combo karty (která NEUKONČÍ tah) vygeneruje zdroje tak, že
    // se teď NEDOSTUPNÁ karta stane dostupnou A je smrtící, zahraj nejdřív tu
    // combo kartu — herní smyčka pak smrtící kartu zahraje a vyhraje.
    // Bez tohoto AI vidí jen jednokrokové lethal a unikne jí výhra (např.
    // Vojenský rozkaz +6 útok → Démon).
    fun comboSetupForLethal(): Card? {
        for (setup in affordable) {
            if (!setup.isCombo) continue
            // Zdroje po zahrání setup karty (zaplať cenu, přičti vygenerované zdroje)
            val after = ai.resources.toMutableMap()
            if (!setup.isXCost) {
                after[setup.costType] = ((after[setup.costType] ?: 0) - setup.effectiveCost).coerceAtLeast(0)
            }
            var generatedAny = false
            for (fx in setup.effects) if (fx is CardEffect.AddResource) {
                after[fx.type] = (after[fx.type] ?: 0) + fx.amount
                generatedAny = true
            }
            if (!generatedAny) continue
            // Existuje karta, která je teď nedostupná, ale po setupu dostupná a lethal?
            val enablesLethal = ai.hand.any { d ->
                d.id != setup.id &&
                (ai.resources[d.costType] ?: 0) < d.effectiveCost &&   // teď nedostupná
                (after[d.costType] ?: 0) >= d.effectiveCost &&          // po setupu dostupná
                isLethal(d, if (d.isXCost) (after[d.costType] ?: 0) else 0)
            }
            if (enablesLethal) return setup
        }
        return null
    }
    comboSetupForLethal()?.let { return AiAction.Play(it) }

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
                // Nahlíží i dovnitř ConditionalEffect – jinak by unikly karty jako
                // Zásobník (staví hrad JEN pod 40 HP): top-level efekt je
                // ConditionalEffect, ne BuildCastle, takže by plochý filtr kartu
                // vyloučil, a ta by pak spadla do fallbacku níže, který ji zahrál
                // i s NESPLNĚNOU podmínkou = vyhozené suroviny bez jakéhokoli efektu.
                fun realizedAttackOrBuild(effects: List<CardEffect>): Boolean = effects.any { fx ->
                    when (fx) {
                        is CardEffect.AttackPlayer, is CardEffect.AttackCastle, is CardEffect.StealCastle -> true
                        is CardEffect.BuildCastle       -> fx.amount > 0
                        is CardEffect.ConditionalEffect ->
                            checkCondition(fx.condition, ai, opponent) && realizedAttackOrBuild(listOf(fx.effect))
                        else -> false
                    }
                }
                val lastChance = scored
                    .filter { (card, _) -> realizedAttackOrBuild(card.effects) }
                    .maxByOrNull { it.second }
                    ?.first
                if (lastChance != null) return AiAction.Play(lastChance)
                // Žádná karta TEĎ neútočí ani nestaví hrad → poslední pokus zahraj JEN
                // pokud karta udělá aspoň NĚCO (effectScore > 0) – jinak (Zásobník
                // s nesplněnou podmínkou, Mirror/Clone bez zdroje = score -100 atd.)
                // by šlo jen o vyhozené suroviny bez jakéhokoli efektu, což je vždy
                // horší než počkat.
                val bestHasEffect = best.effects.sumOf { scoreEffect(it) } > 0
                if (bestScore <= -50 || !bestHasEffect) return AiAction.Wait
                return AiAction.Play(best)
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
        // Jinak čekej. Zahození je čistá ztráta karty: s volným místem v ruce
        // nic neblokuje líz a s prázdným balíčkem není co lízat – suroviny
        // každé kolo rostou, takže se karty časem stanou dostupnými.
        return AiAction.Wait
    }

    // =====================================================================
    // NORMÁLNÍ HRA – vyber nejlepší kartu
    // =====================================================================
    // Předpočítej skóre jednou (score() obsahuje náhodu, nevolej dvakrát)
    val scored = affordable.map { it to score(it) }
    val (best, bestScore) = scored.maxByOrNull { it.second } ?: return AiAction.Wait

    // CloneNextPlayed je aktivní — musíme zahrát kartu, jinak klon přijde vniveč.
    // Ignorujeme práh skóre a hrajeme nejlepší dostupnou kartu.
    if (ai.cloneNextPlayed != null) return AiAction.Play(best)

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
