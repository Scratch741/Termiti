package com.example.termiti

/**
 * Herní AI pro offline mód — heuristické skórování karet.
 *
 * [aiChooseAction] je čistá funkce (závisí pouze na předaných parametrech),
 * nedrží žádný stav — lze volat z libovolného ViewModel nebo testů.
 */

/** Karty, které vkládá rituál kolosu (140 Prastarý kolos → 141 → 142). */
private val RITUAL_STAGE_IDS = setOf("141", "142")

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
    playerWinTarget: Int = 70,
    /** Zahození je 1× za kolo (stejně jako u hráče) – false = už ho AI tento tah použila. */
    canDiscard: Boolean = true,
    /** Hráč právě pasoval. S prázdnými balíčky obou stran ukončí čekání AI hru (resolveByHp). */
    playerWaited: Boolean = false
): AiAction {

    // X-kost karty jsou vždy zahratelné (spotřebují všechen dostupný zdroj, i 0)
    val playable = ai.hand.filter { card ->
        if (card.isXCost) true
        else (ai.resources[card.costType] ?: 0) >= card.effectiveCost
    }

    /**
     * Udělá tenhle seznam efektů TEĎ vůbec něco? Rekurzivní, protože Zrcadlo
     * a Klon jen spouštějí efekty jiné karty – když ta nic neudělá, neudělá nic
     * ani ona. [depth] hlídá řetěz Zrcadlo→Zrcadlo, ať se to nezacyklí.
     */
    fun effectsDoNothing(effects: List<CardEffect>, depth: Int): Boolean =
        effects.isEmpty() || effects.all { fx ->
            when (fx) {
                // Podmínka teď neplatí → vnitřní efekt se vůbec nespustí
                is CardEffect.ConditionalEffect -> !checkCondition(fx.condition, ai, opponent)
                // Zrcadlo kopíruje POSLEDNÍ kartu soupeře; bez ní nemá co dělat
                is CardEffect.Mirror -> {
                    val src = opponent.lastPlayedCard
                    src == null || (depth < 2 && effectsDoNothing(src.effects, depth + 1))
                }
                // Klon kopíruje vlastní poslední kartu. BEZ zdroje má náhradní
                // efekt (+2 magie), takže to no-op není – řeší ho skóre −100.
                is CardEffect.Clone -> {
                    val src = ai.lastPlayedCard
                    src != null && depth < 2 && effectsDoNothing(src.effects, depth + 1)
                }
                // Netransformovaný Shapeshifter: applyEffects je u ShapeShift no-op,
                // proměna probíhá až na začátku tahu. Normálně se AI k takové
                // instanci nedostane (transformShapeShifters běží dřív), tohle je
                // pojistka pro okno, kdy se karta do ruky dostane jinudy.
                is CardEffect.ShapeShift -> true

                // Rozhodovací karty bez zásobníku, ze kterého vybírají: nabídka vyjde
                // prázdná a karta jen zaplatí cenu. Stejný filtr jako v
                // decisionOptions (pasti a mrtvé karty se nenabízejí – Card.isPileJunk).
                // Likvidace pálí ze soupeřova BALÍČKU – s prázdným balíčkem nemá co spálit.
                is CardEffect.DecisionBurnOpponent -> opponent.deck.none { !it.isPileJunk }
                // Vzpomínka bere z vlastního odhazovacího balíčku.
                is CardEffect.DecisionFromDiscard  -> ai.discardPile.none { !it.isPileJunk }
                // Intuice / líz z vlastního balíčku.
                is CardEffect.DecisionFromDeck,
                is CardEffect.DecisionDrawFromDeck -> ai.deck.none { !it.isPileJunk }

                // Krádež i pálení míří do soupeřovy RUKY – prázdná ruka = nic.
                is CardEffect.StealCard,
                is CardEffect.BurnCard,
                is CardEffect.PeekAndStealHand -> opponent.hand.none { !it.isPileJunk }

                else -> false
            }
        }

    /**
     * Karta, která by TEĎ po zahrání neudělala vůbec nic – jen zaplatila cenu
     * (Protiútok s 15 hradbami, Zrcadlo bez soupeřovy poslední karty, Klon
     * kopírující kartu bez efektů…). Počkat je vždycky lepší.
     *
     * Testuje se STRUKTURNĚ, ne přes skóre: scoreEffect() vrací 0 i pro efekty,
     * které nemodeluje, takže filtr podle skóre by vyhazoval i karty, které ve
     * skutečnosti něco dělají.
     */
    fun isNoOpNow(card: Card): Boolean =
        card.effects.isNotEmpty() && effectsDoNothing(card.effects, depth = 0)

    // Filtruje se HNED tady, ne až v rozhodovacím prahu — jinak by karta prošla
    // průchody, které práh skóre obcházejí (aktivní CloneNextPlayed vybírá
    // podle zahrání + hodnoty kopií, endgame fallback bez prahu).
    val affordable = playable.filterNot { isNoOpNow(it) }

    // Situační příznaky
    val aiLowHp        = ai.castleHP < 15
    val aiLowWall      = ai.wallHP   < 5
    val aiWallRoom     = (ai.maxWall - ai.wallHP).coerceAtLeast(0)  // kolik hradeb ještě lze postavit
    val oppLowHp       = opponent.castleHP < 20
    val oppCloseToWin  = opponent.castleHP >= (playerWinTarget - 20) // soupeř je blízko výhry hradem
    val aiCloseToWin   = ai.castleHP       >= (aiWinTarget - 20)    // AI je blízko výhry hradem
    val chaos          = ai.resources[ResourceType.CHAOS] ?: 0
    val bothDecksEmpty = ai.deck.isEmpty() && opponent.deck.isEmpty()
    val handFull       = ai.hand.size >= 7

    // TOTO KOLO buffery – aktivní pokud AI již zahrála TOTO KOLO kartu tento tah
    val buffDrawActive     = ai.drawCardOnPlay.isNotEmpty()
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
                else               -> 5 + fx.amount / 5  // plná hodnota – škáluj jako BuildCastle
            }
        } else {
            fx.amount  // záporné skóre za ztrátu hradeb
        }
        // Převede celé vlastní hradby na hrad – hodnota škáluje s aktuálním HP hradeb.
        is CardEffect.ConvertWallToCastle -> {
            val urgency = if (aiLowHp) 20 else if (aiCloseToWin) 12 else 5
            if (ai.wallHP <= 0) -6 else urgency + ai.wallHP / 4
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
        is CardEffect.AddResource         -> 3 + fx.amount / 2
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
        // Krok rituálu kolosu (141 → 142 → úder za 100): vysoká hodnota, jinak by AI
        // kartu za 20 nikdy nezahrála a v plné ruce ji zahodila jako první.
        is CardEffect.AddCardsToDeck      -> if (fx.cardId in RITUAL_STAGE_IDS) 20 else 4
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
        // Chaotická replikace: základ; cenu kopií dopočítá score() podle nejlepšího cíle (cloneValue)
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
    // ── Chaotická replikace (CloneNextPlayed): vyplatí se kopírovat? ─────────
    // Kopie jdou do balíčku. Mají cenu jen tehdy, když je AI stihne líznout a když
    // jde o karty, které hru posunou – kopie Rychlé magie v pozdní hře jen ředí balíček.

    /** Odhad, kolik tahů hra ještě potrvá: kolik kterémukoli hráči chybí k výhře
     *  (bořením i stavbou), průměrný tah posune hrad zhruba o 6. */
    fun turnsLeftEstimate(): Double {
        val aiDist  = minOf(aiWinTarget - ai.castleHP, opponent.castleHP).coerceAtLeast(0)
        val oppDist = minOf(playerWinTarget - opponent.castleHP, ai.castleHP).coerceAtLeast(0)
        return (minOf(aiDist, oppDist) / 6.0).coerceIn(1.0, 12.0)
    }

    /** Jak moc karta posune hru, když se lízne později (bez ceny). Suroviny a doly
     *  z pozdních kopií za moc nestojí, kopírovací efekty kopírovat nemá smysl. */
    fun cloneQuality(card: Card): Int = card.effects.sumOf { fx ->
        when (fx) {
            is CardEffect.AddResource, is CardEffect.AddResourceDelayed -> 1
            is CardEffect.AddMine -> if (turnsLeftEstimate() >= 6.0) scoreEffect(fx, 0, card.id) else 2
            is CardEffect.CloneNextPlayed, is CardEffect.Clone, is CardEffect.Mirror,
            is CardEffect.AddCardsToDeck -> 0
            else -> scoreEffect(fx, 0, card.id).coerceAtLeast(0)
        }
    }

    /** Hodnota [copies] kopií karty v balíčku = síla × šance, že se kopie stihnou líznout. */
    fun cloneValue(card: Card, copies: Int): Int {
        val drawChance = (turnsLeftEstimate() / (ai.deck.size + copies)).coerceIn(0.0, 1.0)
        return Math.round(copies * cloneQuality(card) * drawChance).toInt()
    }

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

        // ── CloneNextPlayed skóre: hraj jen s hodnotným cílem, na který po zaplacení zbydou zdroje ──
        // CloneNextPlayed (Chaotická replikace) nemá efekt, pokud AI po ní nezahraje další kartu,
        // a škodí, když zkopíruje kartu, která se nestihne líznout nebo nic nepřinese.
        val cloneNext = card.effects.filterIsInstance<CardEffect.CloneNextPlayed>().firstOrNull()
        val hasCloneNextPlayed = cloneNext != null
        val clonePenalty = if (hasCloneNextPlayed) {
            val residualRes = ai.resources.toMutableMap()
            residualRes[card.costType] = ((residualRes[card.costType] ?: 0) - card.effectiveCost).coerceAtLeast(0)
            val followUpCards = ai.hand.filter { other ->
                other.id != card.id && (residualRes[other.costType] ?: 0) >= other.effectiveCost
            }
            when {
                followUpCards.isEmpty() -> -30  // žádná follow-up karta → silná penalta
                else -> {
                    // Hodnota kopií nejlepšího cíle; bez hodnotného cíle (nudná karta,
                    // konec hry) se Replikace nehraje – kopie by jen ředily balíček.
                    val bestClone = followUpCards.maxOf { cloneValue(it, cloneNext!!.count) }
                    if (bestClone < 5) -15 else bestClone
                }
            }
        } else 0

        // ── TOTO KOLO bonus pro odpovídající typ ────────────────────────────────
        // Pokud je aktivní buff z TOTO KOLO karty A tato karta odpovídá jeho typovému
        // filtru, dostane bonus (bez ohledu na isCombo – i nekombo karta buff spustí).
        val totoBuff = run {
            var bonus = 0
            bonus += 10 * ai.drawCardOnPlay.count { typeMatches(it, card.type) }   // každý aktivní líz zvlášť
            if (ai.gainResourcePerCardPlayed.any { typeMatches(it.cardType, card.type) }) bonus += 6
            if (ai.gainCastlePerCardPlayed.any { typeMatches(it.cardType, card.type) }) bonus += 6
            bonus
        }

        return effectScore - costForScore - chaosBlock + totoKoloPenalty + clonePenalty + totoBuff + waitForSetupPenalty + noise
    }

    // Skóre efektů mechaniky "Zahození" (viz [Card.discardEffects]) z pohledu
    // toho, kdo kartu zahazuje. Kladné = zahození je výhodné, záporné = poškodí ho.
    // Samostatná od scoreEffect() – ta u AddResource ignoruje znaménko (vždy
    // kladné skóre), zatímco tady musí záporná hodnota (self-harm) vyjít záporně.
    fun scoreDiscardEffect(fx: CardEffect): Int = when (fx) {
        is CardEffect.AddResource   -> fx.amount * 3
        is CardEffect.AddMine       -> fx.amount * 9
        is CardEffect.BuildCastle   -> fx.amount * 2
        is CardEffect.BuildWall     -> fx.amount
        is CardEffect.AttackCastle  -> fx.amount / 2   // poškodí soupeře → dobré pro discardera
        is CardEffect.AttackPlayer  -> fx.amount / 3
        is CardEffect.AttackWall    -> fx.amount / 3
        is CardEffect.DrawCard      -> fx.count * 4
        is CardEffect.StealResource -> fx.amount * 4
        is CardEffect.DrainResource -> fx.amount * 2
        is CardEffect.BurnCard      -> 6
        is CardEffect.StealCard     -> 6
        else                        -> 0
    }

    // Chytrý výběr karty k zahození:
    // Zahodí kartu s nejnižší "hodnotou v ruce":
    //   hodnotaVRuce = síla efektů × 2  −  táhla_do_dovolení × 3
    // Silné karty (Démon, drak…) se drží i za cenu dlouhého čekání.
    // Slabé karty, na které se navíc čeká, jsou první kandidáti na zahození.
    fun bestDiscard(): Card? = ai.hand.minByOrNull { card ->
        // Karta, která by teď neudělala nic (Likvidace bez soupeřova balíčku,
        // Zrcadlo bez zdroje…), nemá v ruce hodnotu – zahoď ji jako první.
        val effectScore   = if (isNoOpNow(card)) 0
                            else card.effects.sumOf { scoreEffect(it) }.coerceAtLeast(0)
        val shortfall     = (card.effectiveCost - (ai.resources[card.costType] ?: 0)).coerceAtLeast(0)
        val mineRate      = (ai.mines[card.costType] ?: 0).coerceAtLeast(1)
        // Penalizace za nedostupnost je stropována na 4 tahy — drahé late-game karty
        // se nesmí zahazovat jen proto, že jsou teď nedostupné.
        val turnsToAfford = (shortfall.toFloat() / mineRate).coerceAtMost(4f)
        // Malý bonus za cenu: dražší karta = silnější late-game potenciál.
        val costBonus     = card.effectiveCost / 2
        // Mechanika "Zahození": záporné discardScore (self-harm) kartu chrání
        // (skóre roste → míň pravděpodobná k zahození); kladné ji zvýhodní.
        val discardScore  = card.discardEffects.sumOf { scoreDiscardEffect(it) }
        effectScore * 2 - turnsToAfford * 2 + costBonus - discardScore
    }

    // ── Záměrné zahození ────────────────────────────────────────────────────
    // Některé karty mají cennější efekt při ZAHOZENÍ než při zahrání (Zapomenutá
    // poznámka: zahrát = +1 magie čistého, zahodit = líznout kartu). Zahození je
    // stejně jako u hráče 1× za kolo a tah NEUKONČUJE – nestojí tedy zahrání, jen
    // samotnou kartu. Hodnotu zahození proto porovnáváme s hodnotou PONECHÁNÍ té
    // karty (co by přinesla zahraná), ve stejné škále jako score()/scoreEffect() –
    // na rozdíl od scoreDiscardEffect(), která je bezkontextová (řazení v bestDiscard()).
    fun discardValueNow(card: Card): Int = card.discardEffects.sumOf { fx ->
        when (fx) {
            is CardEffect.DrawCard -> {
                if (ai.deck.isEmpty()) 0 else {
                    val handAfter = ai.hand.size - 1                     // zahazovaná karta odejde
                    val slots     = (7 - handAfter).coerceAtLeast(0)
                    val useful    = minOf(fx.count, slots, ai.deck.size)
                    // Málo karet v ruce = málo možností → líz je cennější
                    useful * (if (handAfter <= 2) 7 else 5)
                }
            }
            is CardEffect.AddResource ->
                if (fx.amount > 0) 3 + fx.amount / 2 else scoreDiscardEffect(fx)   // škála jako scoreEffect
            else -> scoreDiscardEffect(fx)   // záporné (self-harm) zůstanou záporné
        }
    }

    /** Co by karta přinesla zahraná (bez šumu) – cena, o kterou zahozením přijdeme. */
    fun keepValue(card: Card): Int =
        card.effects.sumOf { scoreEffect(it, 0, card.id) } - card.effectiveCost

    /**
     * Karta, jejíž zahození je teď výhodnější než si ji nechat, nebo null.
     * Null i tehdy, když už AI tento tah zahazovala ([canDiscard] = false).
     */
    fun bestDeliberateDiscard(): Card? = if (!canDiscard) null else ai.hand
        .filter { it.discardEffects.isNotEmpty() }
        .map { it to discardValueNow(it) - keepValue(it) }
        .filter { (card, gain) -> gain > 0 && discardValueNow(card) > 0 }
        .maxByOrNull { it.second }
        ?.first

    // ── Zahození, aby si AI něco dovolila ───────────────────────────────────
    // Zahození s efektem „+N suroviny" (Zoufalý žold: útok +3, Osudová mince: chaos +2)
    // tah neukončí a přidané suroviny jdou utratit hned. Pokud tím AI zpřístupní kartu,
    // na kterou teď nemá, a ta má větší hodnotu než zahazovaná karta i než to, co by
    // jinak zahrála ([bestNow]), vyplatí se zahodit – herní smyčka pak zpřístupněnou
    // kartu zahraje. Smrtící karta má skóre ≥ 1000, takže zahození kvůli výhře projde vždy.
    fun discardToAfford(bestNow: Int): Card? {
        if (!canDiscard) return null
        var pick: Card? = null
        var pickNet = 0
        for (d in ai.hand) {
            val gains = d.discardEffects.filterIsInstance<CardEffect.AddResource>().filter { it.amount > 0 }
            if (gains.isEmpty()) continue
            val after = ai.resources.toMutableMap()
            gains.forEach { g -> after[g.type] = (after[g.type] ?: 0) + g.amount }
            for (t in ai.hand) {
                if (t === d || t.isXCost || isNoOpNow(t)) continue
                if ((ai.resources[t.costType] ?: 0) >= t.effectiveCost) continue   // dostupná už teď
                if ((after[t.costType] ?: 0) < t.effectiveCost) continue           // ani po zahození ne
                val net = score(t) - keepValue(d)
                if (net > 0 && net > bestNow && net > pickNet) { pick = d; pickNet = net }
            }
        }
        return pick
    }

    // ── Zahození pro suroviny ───────────────────────────────────────────────
    // Zahození „+N suroviny" je zdarma (tah nekončí) a surovina jde hned utratit. Má-li
    // AI v ruce jinou kartu té suroviny, přidané suroviny opravdu využije – zahodit se
    // pak vyplatí víc než kartu zahrát (Zoufalý žold: útok +3 místo „zaútoč za 5 za 2").
    // score() to nevidí: každý útok dostává pevný základ, malý útok tak přebije suroviny.
    // Ověřeno v simulátoru (deckbuilder.html): Žold zahazovaný tímto pravidlem je v AI
    // Útočníkovi lepší než Rabování i Válečný sekyrník (+3,5 bodu), hraný jen −2,4.
    fun discardForResources(): Card? {
        if (!canDiscard) return null
        return ai.hand.filter { d ->
            val gain = d.discardEffects.filterIsInstance<CardEffect.AddResource>()
                .firstOrNull { it.amount > 0 } ?: return@filter false
            ai.hand.any { t -> t !== d && !t.isXCost && t.costType == gain.type }
        }.maxByOrNull { d ->
            d.discardEffects.filterIsInstance<CardEffect.AddResource>().sumOf { it.amount.coerceAtLeast(0) }
        }
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
    // AI zde NEodhazuje: s prázdným balíčkem se nic nelízne, zahozením by jen přišla
    // o kartu. Vždy zahraj nebo ČEKEJ – čekání obou s prázdnými balíčky spouští resolveByHp.
    if (bothDecksEmpty) {
        // Hráč pasoval a oba balíčky jsou prázdné → čekání TEĎ vyvolá resolveByHp,
        // který porovná hrady. S vyšším hradem je to jistá výhra: hrát cokoli dál
        // jen dává hráči další kolo, ve kterém může náskok dorovnat.
        // (Shoda hradů = remíza, tam se čekat nevyplatí a AI hraje dál.)
        if (playerWaited && ai.castleHP > opponent.castleHP) return AiAction.Wait

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
        // Zahození, které zpřístupní kartu (tah nekončí – hned se zahraje)
        discardToAfford(0)?.let { return AiAction.Discard(it) }
        discardForResources()?.let { return AiAction.Discard(it) }
        // Užitečné zahození (tah nekončí – líznutá karta se může hned zahrát)
        bestDeliberateDiscard()?.let { return AiAction.Discard(it) }
        // Plná ruka + balíček má karty → zahoď nejhorší kartu
        // (čekání by způsobilo spálení líznuté karty, protože ruka je plná)
        if (canDiscard && handFull && ai.deck.isNotEmpty()) {
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

    // CloneNextPlayed je aktivní – další zahraná karta se zkopíruje do balíčku. Vyber
    // kartu podle toho, co přinese teď, I podle hodnoty jejích kopií (Temný přenos,
    // ne Rychlá magie). Smrtící tah (≥ 1000) vyhraje vždy. Když nic nestojí za to,
    // radši nehraj nic, než nakopírovat do balíčku kartu, která jen ředí.
    val pendingClones = ai.cloneNextPlayed
    if (pendingClones != null) {
        val (pick, combined) = scored.map { (c, s) -> c to s + cloneValue(c, pendingClones) }
            .maxByOrNull { it.second } ?: return AiAction.Wait
        return if (combined > 0) AiAction.Play(pick) else AiAction.Wait
    }

    // Užitečné zahození jde PŘED zahráním: tah nekončí a líznutá karta může být
    // lepší než cokoli v ruce. Smrtící tah (skóre ≥ 1000) má ale vždy přednost.
    if (bestScore < 1000) {
        discardToAfford(bestScore.coerceAtLeast(0))?.let { return AiAction.Discard(it) }
        discardForResources()?.let { return AiAction.Discard(it) }
        bestDeliberateDiscard()?.let { return AiAction.Discard(it) }
    }

    // Pokud je i nejlepší karta nevýhodná (podmínka nesplněna, čisté náklady):
    // – plná ruka → zahoď nejhorší kartu (uvolni místo pro lepší líz)
    // – jinak → čekej
    if (bestScore <= 0) {
        return if (canDiscard && handFull && ai.deck.isNotEmpty()) {
            bestDiscard()?.let { AiAction.Discard(it) } ?: AiAction.Wait
        } else {
            AiAction.Wait
        }
    }

    return AiAction.Play(best)
}
