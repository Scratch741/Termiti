package com.example.termiti

/**
 * Sdílená herní logika – použitelná v GameViewModel i OnlineLobbyViewModel.
 *
 * Přidání nového efektu:
 *  1. Přidej data class do CardEffect.kt
 *  2. Přidej when-branch sem do applyEffects()
 *  3. Přidej karty do allCards v GameViewModel
 */

fun applyEffects(
    effects:  List<CardEffect>,
    self:     PlayerState,
    opponent: PlayerState,
    allCards: List<Card>,
    xValue:   Int = 0,
    onOpponentCardLost: ((Card, CardAction) -> Unit)? = null,
    onDrawCard: ((PlayerState, Int) -> Unit)? = null,
    maxHandSize: Int? = null,
    onSelfCardLost: ((Card, CardAction) -> Unit)? = null
) {
    for (effect in effects) when (effect) {
        is CardEffect.AddResource   ->
            self.resources[effect.type] = ((self.resources[effect.type] ?: 0) + effect.amount).coerceIn(0, MAX_RESOURCE)

        is CardEffect.AddResourceDelayed ->
            self.pendingResources.add(PendingResource(effect.type, effect.amount, effect.turns))

        is CardEffect.AddMine       ->
            self.mines[effect.type] = ((self.mines[effect.type] ?: 0) + effect.amount).coerceAtMost(MAX_MINES)

        is CardEffect.BuildWall     ->
            self.wallHP = (self.wallHP + effect.amount).coerceIn(0, MAX_WALL)

        is CardEffect.BuildCastle   ->
            self.castleHP = (self.castleHP + effect.amount).coerceAtMost(100)

        is CardEffect.AttackPlayer  -> {
            val dmg      = effect.amount.coerceAtMost(opponent.wallHP)
            opponent.wallHP -= dmg
            val overflow = effect.amount - dmg
            if (overflow > 0) opponent.castleHP -= overflow
        }

        is CardEffect.AttackWall    ->
            opponent.wallHP = (opponent.wallHP - effect.amount).coerceAtLeast(0)

        is CardEffect.AttackCastle  ->
            opponent.castleHP -= effect.amount

        is CardEffect.StealResource -> {
            val taken = minOf(effect.amount, opponent.resources[effect.type] ?: 0)
            opponent.resources[effect.type] = (opponent.resources[effect.type] ?: 0) - taken
            self.resources[effect.type]     = ((self.resources[effect.type]     ?: 0) + taken).coerceAtMost(MAX_RESOURCE)
        }

        is CardEffect.DrainResource -> {
            val drained = minOf(effect.amount, opponent.resources[effect.type] ?: 0)
            opponent.resources[effect.type] = (opponent.resources[effect.type] ?: 0) - drained
        }

        is CardEffect.ConditionalEffect ->
            if (checkCondition(effect.condition, self, opponent))
                applyEffects(listOf(effect.effect), self, opponent, allCards, xValue, onOpponentCardLost, onDrawCard)

        is CardEffect.DestroyMine   -> {
            val cur = opponent.mines[effect.type] ?: 0
            val min = if (effect.type == ResourceType.CHAOS) 0 else 1
            if (cur > min) opponent.mines[effect.type] = (cur - effect.amount).coerceAtLeast(min)
        }

        is CardEffect.ConvertMine   -> {
            val floor   = if (effect.from == ResourceType.CHAOS) 0 else 1
            val curFrom = self.mines[effect.from] ?: 0
            // Chaos důl se vždy zvýší; zdrojový důl se sníží jen pokud je nad floorem
            self.mines[effect.to] = ((self.mines[effect.to] ?: 0) + 1).coerceAtMost(MAX_MINES)
            if (curFrom > floor) self.mines[effect.from] = curFrom - 1
        }

        is CardEffect.BlockMine     -> {
            // Přičti blokovací kola (stackuje se, ale max 5 kol)
            val current = opponent.mineBlockedTurns[effect.type] ?: 0
            opponent.mineBlockedTurns[effect.type] = (current + effect.turns).coerceAtMost(5)
        }

        is CardEffect.StealCard     -> repeat(effect.count) {
            if (opponent.hand.isNotEmpty()) {
                val stolen = opponent.hand.random()
                opponent.hand.remove(stolen)
                if (self.hand.size < (maxHandSize ?: 7)) {
                    self.hand.add(stolen.copy(isGenerated = true))
                } else {
                    self.discardPile.add(stolen)   // ruka plná → ukradená karta shoří
                }
                onOpponentCardLost?.invoke(stolen, CardAction.STOLEN)
            }
        }

        is CardEffect.BurnCard      -> repeat(effect.count) {
            if (opponent.hand.isNotEmpty()) {
                val burned = opponent.hand.random()
                opponent.hand.remove(burned)
                opponent.discardPile.add(burned)
                onOpponentCardLost?.invoke(burned, CardAction.BURNED)
            }
        }

        is CardEffect.AddCardsToDeck -> {
            val template = allCards.find { it.id == effect.cardId }
            if (template != null) {
                repeat(effect.count) {
                    self.deck.add(template.copy(id = "${template.id}_${java.util.UUID.randomUUID()}", isGenerated = true))
                }
                self.deck.shuffle()
            }
        }

        is CardEffect.AddToOpponentDeck -> {
            val template = allCards.find { it.id == effect.cardId }
            if (template != null) {
                repeat(effect.count) {
                    opponent.deck.add(template.copy(id = "${template.id}_${java.util.UUID.randomUUID()}", isGenerated = true))
                }
                opponent.deck.shuffle()
            }
        }

        // Pasca se spustí při líznutí v PlayerState.drawCards — při zahraní karty je to no-op
        is CardEffect.TrapOnDraw -> { }

        is CardEffect.DrawCard ->
            if (onDrawCard != null) onDrawCard(self, effect.count)
            else {
                // Reportuj spálené overdraw karty a pasti – jinak se neukážou
                // v odhazovacím balíčku ani v logu
                val r = self.drawCards(effect.count)
                (r.burned + r.traps).forEach { onSelfCardLost?.invoke(it, CardAction.BURNED) }
            }

        is CardEffect.DrawBoth -> {
            if (onDrawCard != null) onDrawCard(self, effect.count)
            else {
                val r = self.drawCards(effect.count)
                (r.burned + r.traps).forEach { onSelfCardLost?.invoke(it, CardAction.BURNED) }
            }
            // Studna vědomostí apod.: líže i SOUPEŘ – jeho spálené overdraw karty
            // a pasti se musí reportovat (odhazovací balíček + log)
            val oppResult = opponent.drawCards(effect.count)
            (oppResult.burned + oppResult.traps).forEach {
                onOpponentCardLost?.invoke(it, CardAction.BURNED)
            }
        }

        is CardEffect.CloneNextPlayed -> self.cloneNextPlayed = effect.count

        is CardEffect.NextCardIsCombo -> self.nextCardIsCombo = true

        is CardEffect.DiscountRandomCard -> {
            val matching = self.hand.indices.filter { i ->
                val card = self.hand[i]
                (effect.costType == null || card.costType == effect.costType) &&
                (effect.cardType == null || card.type == effect.cardType)
            }
            matching.shuffled().take(effect.count).forEach { i ->
                self.hand[i] = self.hand[i].copy(costModifier = self.hand[i].costModifier - effect.delta)
            }
        }

        is CardEffect.StealCastle -> {
            val stolen = minOf(effect.amount, opponent.castleHP.coerceAtLeast(0))
            opponent.castleHP -= stolen
            self.castleHP = (self.castleHP + stolen).coerceAtMost(100)
        }

        is CardEffect.DrawPerCardPlayed -> self.drawCardOnPlay = effect.cardType ?: ""

        is CardEffect.GainResourcePerCardPlayed ->
            self.gainResourcePerCardPlayed.add(effect)

        is CardEffect.GainCastlePerCardPlayed ->
            self.gainCastlePerCardPlayed.add(effect)

        is CardEffect.SwapHands -> {
            val selfOldHand     = self.hand.toList()
            val opponentOldHand = opponent.hand.toList()
            self.hand.clear()
            self.hand.addAll(opponentOldHand.map { it.copy(isGenerated = true) })
            opponent.hand.clear()
            opponent.hand.addAll(selfOldHand.map { it.copy(isGenerated = true) })
            // Zaloguj každou kartu z původní soupeřovy ruky jako ukradenou
            for (card in opponentOldHand) {
                onOpponentCardLost?.invoke(card, CardAction.STOLEN)
            }
        }

        is CardEffect.ModifyHandCost -> {
            val target = if (effect.targetOpponent) opponent else self
            val iter = target.hand.listIterator()
            while (iter.hasNext()) {
                val card = iter.next()
                iter.set(card.copy(costModifier = card.costModifier + effect.delta))
            }
        }

        is CardEffect.GiveRandomCard -> {
            val pool = allCards.filter { it.costType == effect.costType && !it.isPlaceholder }
            if (pool.isNotEmpty()) {
                val tmpl = pool.random()
                val card = tmpl.copy(
                    id          = "${tmpl.id}_${java.util.UUID.randomUUID()}",
                    isGenerated = true
                )
                val maxHand = maxHandSize ?: 7
                if (self.hand.size < maxHand) self.hand.add(card)
                else self.discardPile.add(card)
            }
        }

        is CardEffect.RandomizeHands -> {
            val selfCount    = self.hand.size
            val oppCount     = opponent.hand.size
            val selfSnapshot = self.hand.toList()
            self.discardPile.addAll(self.hand)
            self.hand.clear()
            selfSnapshot.forEach { onSelfCardLost?.invoke(it, CardAction.BURNED) }
            val oppSnapshot  = opponent.hand.toList()
            opponent.discardPile.addAll(opponent.hand)
            opponent.hand.clear()
            oppSnapshot.forEach { onOpponentCardLost?.invoke(it, CardAction.BURNED) }
            if (onDrawCard != null) onDrawCard(self, selfCount)
            else self.drawCards(selfCount)
            opponent.drawCards(oppCount)
        }

        // ── X-kost efekty ─────────────────────────────────────────────────────
        is CardEffect.XScaledAttackPlayer -> {
            val dmg     = xValue / effect.divisor
            val wallDmg = dmg.coerceAtMost(opponent.wallHP)
            opponent.wallHP -= wallDmg
            val overflow = dmg - wallDmg
            if (overflow > 0) opponent.castleHP -= overflow
        }

        is CardEffect.XScaledAttackCastle ->
            opponent.castleHP -= xValue / effect.divisor

        is CardEffect.XScaledBuildCastle ->
            self.castleHP = (self.castleHP + xValue / effect.divisor).coerceAtMost(100)

        is CardEffect.XScaledDualResource -> {
            val amount = xValue / effect.divisor
            self.resources[effect.typeA] = ((self.resources[effect.typeA] ?: 0) + amount).coerceAtMost(MAX_RESOURCE)
            self.resources[effect.typeB] = ((self.resources[effect.typeB] ?: 0) + amount).coerceAtMost(MAX_RESOURCE)
        }

        // Pasivní příznak – transformace probíhá v transformShapeShifters() při startu tahu
        is CardEffect.ShapeShift -> { /* no-op */ }

        is CardEffect.MomentumAttack -> {
            val total = effect.base + self.attackCardsThisTurn * effect.bonusPerAttack
            val dmg   = total.coerceAtMost(opponent.wallHP)
            opponent.wallHP -= dmg
            val overflow = total - dmg
            if (overflow > 0) opponent.castleHP -= overflow
        }

        // Rozhodnutí – asynchronní zpracování ve ViewModelu; applyEffects je pouze no-op
        is CardEffect.DecisionBurnOpponent  -> { /* řeší ViewModel */ }
        is CardEffect.DecisionChooseType    -> { /* řeší ViewModel */ }
        is CardEffect.DecisionFromDiscard   -> { /* řeší ViewModel */ }
        is CardEffect.DecisionFromDeck      -> { /* řeší ViewModel */ }
        is CardEffect.DecisionDrawFromDeck  -> { /* řeší ViewModel */ }
        is CardEffect.DecisionMine          -> { /* řeší ViewModel */ }
        is CardEffect.PeekAndStealHand      -> { /* řeší ViewModel */ }
        is CardEffect.SmartJoker            -> { /* řeší ViewModel */ }
        is CardEffect.DecisionChooseResource -> { /* řeší ViewModel */ }

        is CardEffect.Mirror -> {
            val src = opponent.lastPlayedCard
            if (src != null) {
                // X-kost karta: spotřebuj vlastní zdroje jako X, pak kopíruj efekty
                val mirrorX = if (src.isXCost) {
                    val x = self.resources[src.costType] ?: 0
                    self.resources[src.costType] = 0
                    x
                } else xValue
                applyEffects(src.effects, self, opponent, allCards, mirrorX, onOpponentCardLost, onDrawCard)
            }
        }

        is CardEffect.Clone -> {
            val src = self.lastPlayedCard
            if (src != null) {
                // X-kost karta: spotřebuj zbývající zdroje jako X, pak kopíruj efekty
                val cloneX = if (src.isXCost) {
                    val x = self.resources[src.costType] ?: 0
                    self.resources[src.costType] = 0
                    x
                } else xValue
                applyEffects(src.effects, self, opponent, allCards, cloneX, onOpponentCardLost, onDrawCard)
            } else {
                // Fallback: hráč ještě nic nezahrál → +2 magie
                self.resources[ResourceType.MAGIC] = ((self.resources[ResourceType.MAGIC] ?: 0) + 2).coerceAtMost(MAX_RESOURCE)
            }
        }
    }
}

/**
 * Aktualizuje vizuální vzhled a cenu Klon karet v [hand] podle [playerLastPlayed].
 * Klon stojí o 1 více než originál a zobrazuje jeho art + popis.
 */
fun updateCloneCards(hand: MutableList<Card>, playerLastPlayed: Card?, allCards: List<Card>) {
    for (i in hand.indices) {
        val card = hand[i]
        if (!card.effects.any { it is CardEffect.Clone }) continue
        // Zachovej Kletbu cen / slevy (ModifyHandCost) na Klonu samotném:
        // costModifier karty už může obsahovat +1 přirážku z předchozího běhu
        // této funkce (poznáme podle localizationId) – odečti ji, zbytek je kletba.
        val curse = card.costModifier - (if (card.localizationId == "__clone__") 1 else 0)
        hand[i] = if (playerLastPlayed != null) {
            val srcId = playerLastPlayed.localizationId ?: playerLastPlayed.baseId
            val resolvedDesc = LanguageManager.cardDesc(srcId, playerLastPlayed.description)
            // Předvyřeš i jméno – bez toho by fallback vrátil "Klon" (CS) i v EN
            val resolvedName = LanguageManager.cardName(card.baseId, card.name)
            card.copy(
                localizationId = "__clone__",
                name           = resolvedName,
                // Klon stojí effectiveCost originálu +1 – zohledňuje případnou slevu na originálu
                cost           = playerLastPlayed.effectiveCost,
                costModifier   = 1 + curse,
                costType       = playerLastPlayed.costType,
                artResId       = playerLastPlayed.artResId,
                artBiasX       = playerLastPlayed.artBiasX,
                artBiasY       = playerLastPlayed.artBiasY,
                artScale       = playerLastPlayed.artScale,
                description    = resolvedDesc,
                type           = playerLastPlayed.type,
                // Zvuk kopírované karty: efekty Klonu ([Clone]) by auto-detekce
                // vyhodnotila na generický zvuk → zapeč zvuk zdroje explicitně
                soundResId     = playerLastPlayed.soundResId,
                sound          = playerLastPlayed.sound ?: detectCardSound(playerLastPlayed),
                overlayEffects = playerLastPlayed.effects.filterIsInstance<CardEffect.ConditionalEffect>()
            )
        } else {
            val orig         = allCards.find { it.baseId == card.baseId }
            val resolvedName = LanguageManager.cardName(card.baseId, card.name)
            card.copy(
                localizationId = null,
                name           = resolvedName,
                cost           = orig?.cost     ?: card.cost,
                costModifier   = curse,
                costType       = orig?.costType ?: card.costType,
                artResId       = orig?.artResId,
                artBiasX       = orig?.artBiasX ?: 0f,
                artBiasY       = orig?.artBiasY ?: 0f,
                artScale       = orig?.artScale  ?: 1f,
                description    = orig?.description ?: card.description,
                type           = orig?.type        ?: card.type,
                soundResId     = orig?.soundResId,
                sound          = orig?.sound
            )
        }
    }
}

/** Resetuje Mirror/Clone kartu v [pile] (discardPile/deck) na originální data z [allCards]. */
fun resetMirrorCloneInPile(pile: MutableList<Card>, card: Card, allCards: List<Card>) {
    if (card.effects.none { it is CardEffect.Mirror || it is CardEffect.Clone }) return
    val orig = allCards.find { it.baseId == card.baseId } ?: return
    val idx  = pile.indexOfLast { it.id == card.id }
    if (idx >= 0) pile[idx] = orig.copy(id = card.id, isGenerated = card.isGenerated)
}

/**
 * Aktualizuje vizuální vzhled Mirror karet v [hand] podle [opponentLastPlayed].
 * Zachovává ID, cenu a efekty — mění pouze art a popis.
 * Volat po každém tahu soupeře (v GameViewModel).
 */
fun updateMirrorCards(hand: MutableList<Card>, opponentLastPlayed: Card?, allCards: List<Card>) {
    for (i in hand.indices) {
        val card = hand[i]
        if (!card.effects.any { it is CardEffect.Mirror }) continue
        hand[i] = if (opponentLastPlayed != null) {
            val srcId = opponentLastPlayed.localizationId ?: opponentLastPlayed.baseId
            val resolvedDesc = LanguageManager.cardDesc(srcId, opponentLastPlayed.description)
            val resolvedName = LanguageManager.cardName(card.baseId, card.name)
            card.copy(
                localizationId = "__mirror__",
                name           = resolvedName,
                artResId       = opponentLastPlayed.artResId,
                artBiasX       = opponentLastPlayed.artBiasX,
                artBiasY       = opponentLastPlayed.artBiasY,
                artScale       = opponentLastPlayed.artScale,
                description    = resolvedDesc,
                type           = opponentLastPlayed.type,
                // Zvuk kopírované karty (efekty Zrcadla by daly generický zvuk)
                soundResId     = opponentLastPlayed.soundResId,
                sound          = opponentLastPlayed.sound ?: detectCardSound(opponentLastPlayed),
                overlayEffects = opponentLastPlayed.effects.filterIsInstance<CardEffect.ConditionalEffect>()
            )
        } else {
            val orig         = allCards.find { it.baseId == card.baseId }
            val resolvedName = LanguageManager.cardName(card.baseId, card.name)
            card.copy(
                localizationId = null,
                name           = resolvedName,
                artResId       = orig?.artResId,
                artBiasX       = orig?.artBiasX ?: 0f,
                artBiasY       = orig?.artBiasY ?: 0f,
                artScale       = orig?.artScale  ?: 1f,
                description    = orig?.description ?: card.description,
                type           = orig?.type     ?: card.type,
                soundResId     = orig?.soundResId,
                sound          = orig?.sound
            )
        }
    }
}

/**
 * Vrátí true pokud je karta (nebo její instance) Shapeshifter.
 * Kontroluje jak ShapeShift efekt (originál z balíčku), tak ID prefix "C34"
 * (po transformaci karta ztratí ShapeShift efekt, ale ID zachová prefix).
 */
fun Card.isShapeShifterInstance(): Boolean =
    effects.any { it is CardEffect.ShapeShift } || id.substringBefore("_") == "C34"

/**
 * Transformuje Shapeshifter karty v ruce v náhodné karty z [pool].
 *
 * @param onlyNew  true  → transformuj POUZE netransformované karty (mají stále `ShapeShift` efekt);
 *                        vhodné pro líz uprostřed tahu, aby se již transformované instance nemenily.
 *                 false → transformuj VŠECHNY instance (normální použití na začátku tahu).
 *
 * Volá se na ZAČÁTKU každého tahu (onlyNew = false) i po každém lízu uprostřed tahu (onlyNew = true).
 * Původní instance ID je zachováno, aby Compose LazyRow nerekrekoval slot.
 */
fun transformShapeShifters(hand: MutableList<Card>, pool: List<Card>, onlyNew: Boolean = false) {
    val validPool = pool.filter { tmpl -> tmpl.effects.none { it is CardEffect.ShapeShift } && !tmpl.isPlaceholder }
    if (validPool.isEmpty()) return
    for (i in hand.indices) {
        val shouldTransform = if (onlyNew)
            hand[i].effects.any { it is CardEffect.ShapeShift }   // jen nové, netransformované
        else
            hand[i].isShapeShifterInstance()                       // všechny instance
        if (shouldTransform) {
            val prev = hand[i]
            val tmpl = validPool.random()
            // id stále začíná "C34_" → příští kolo se znovu transformuje
            // costModifier se zachová — sleva od Chaotického mudrce (DecisionChooseType) platí i po transformaci
            // Jméno zůstává "Shapeshifter" (jako Klon/Zrcadlo si drží vlastní jméno);
            // popis je předvyřešen z transformované karty; sentinel "__shapeshifter__" zabraňuje
            // lang lookupu přepsat jméno/popis při recompose.
            val resolvedName = LanguageManager.cardName(prev.baseId, prev.name)
            val resolvedDesc = LanguageManager.cardDesc(tmpl.baseId, tmpl.description)
            hand[i] = tmpl.copy(
                id             = prev.id,
                costModifier   = prev.costModifier,
                name           = resolvedName,
                description    = resolvedDesc,
                localizationId = "__shapeshifter__"
            )
        }
    }
}

fun checkCondition(condition: Condition, player: PlayerState, opponent: PlayerState? = null): Boolean = when (condition) {
    // "Máš X surovin" se vyhodnocuje proti stavu PŘED zaplacením ceny karty
    // (jinak by karta nemohla splnit vlastní podmínku – viz preCostResources).
    is Condition.ResourceAbove -> {
        val r = player.preCostResources ?: player.resources
        (r[condition.type] ?: 0) > condition.threshold
    }
    is Condition.WallAbove     -> player.wallHP   > condition.threshold
    is Condition.WallBelow     -> player.wallHP   < condition.threshold
    is Condition.CastleAbove    -> player.castleHP > condition.threshold
    is Condition.CastleBelow    -> player.castleHP < condition.threshold
    is Condition.LastPlayedType -> player.lastPlayedType == condition.cardType
    is Condition.ResourceMoreThanOpponent -> {
        val r = player.preCostResources ?: player.resources
        val playerRes   = r[condition.type]        ?: 0
        val opponentRes = opponent?.resources?.get(condition.type) ?: 0
        playerRes > opponentRes
    }
}
