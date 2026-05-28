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
    onDrawCard: ((PlayerState, Int) -> Unit)? = null
) {
    for (effect in effects) when (effect) {
        is CardEffect.AddResource   ->
            self.resources[effect.type] = ((self.resources[effect.type] ?: 0) + effect.amount).coerceAtMost(MAX_RESOURCE)

        is CardEffect.AddResourceDelayed ->
            self.pendingResources.add(PendingResource(effect.type, effect.amount, effect.turns))

        is CardEffect.AddMine       ->
            self.mines[effect.type] = ((self.mines[effect.type] ?: 0) + effect.amount).coerceAtMost(MAX_MINES)

        is CardEffect.BuildWall     ->
            self.wallHP = (self.wallHP + effect.amount).coerceIn(0, 100)

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
                if (self.hand.size < 7) {
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
            else self.drawCards(effect.count)   // přebytečné karty shoří (hand full → discardPile)

        is CardEffect.DrawBoth -> {
            if (onDrawCard != null) onDrawCard(self, effect.count)
            else self.drawCards(effect.count)
            opponent.drawCards(effect.count)
        }

        is CardEffect.CloneNextPlayed -> self.cloneNextPlayed = effect.count

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
                val card = pool.random().copy(isGenerated = true)
                if (self.hand.size < 7) self.hand.add(card)
                else self.discardPile.add(card)
            }
        }

        is CardEffect.RandomizeHands -> {
            val selfCount = self.hand.size
            val oppCount  = opponent.hand.size
            self.discardPile.addAll(self.hand)
            self.hand.clear()
            opponent.discardPile.addAll(opponent.hand)
            opponent.hand.clear()
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
        is CardEffect.DecisionMine          -> { /* řeší ViewModel */ }
        is CardEffect.PeekAndStealHand      -> { /* řeší ViewModel */ }
        is CardEffect.SmartJoker            -> { /* řeší ViewModel */ }
        is CardEffect.DecisionChooseResource -> { /* řeší ViewModel */ }
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
 * Transformuje všechny Shapeshifter karty v ruce v náhodné karty z [pool].
 * Volá se na ZAČÁTKU každého tahu (po lízu) — karta se mění každé kolo.
 * Původní instance ID je zachováno, aby Compose LazyRow nerekrekoval slot.
 */
fun transformShapeShifters(hand: MutableList<Card>, pool: List<Card>) {
    val validPool = pool.filter { tmpl -> tmpl.effects.none { it is CardEffect.ShapeShift } && !tmpl.isPlaceholder }
    if (validPool.isEmpty()) return
    for (i in hand.indices) {
        if (hand[i].isShapeShifterInstance()) {
            val prev = hand[i]
            val tmpl = validPool.random()
            // id stále začíná "C34_" → příští kolo se znovu transformuje
            // costModifier se zachová — sleva od Chaotického mudrce (DecisionChooseType) platí i po transformaci
            hand[i] = tmpl.copy(id = prev.id, costModifier = prev.costModifier)
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
