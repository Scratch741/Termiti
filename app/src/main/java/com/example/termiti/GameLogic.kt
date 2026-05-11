package com.example.termiti

/**
 * Sdílená herní logika – použitelná v GameViewModel i MultiplayerViewModel.
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
                    self.hand.add(stolen)
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
                    self.deck.add(template.copy(id = "${template.id}_${java.util.UUID.randomUUID()}"))
                }
                self.deck.shuffle()
            }
        }

        is CardEffect.DrawCard ->
            if (onDrawCard != null) onDrawCard(self, effect.count)
            else self.drawCards(effect.count)   // přebytečné karty shoří (hand full → discardPile)

        is CardEffect.StealCastle -> {
            val stolen = minOf(effect.amount, opponent.castleHP.coerceAtLeast(0))
            opponent.castleHP -= stolen
            self.castleHP = (self.castleHP + stolen).coerceAtMost(100)
        }

        is CardEffect.DrawPerCardPlayed -> self.drawCardOnPlay = true

        is CardEffect.GainResourcePerCardPlayed ->
            self.gainResourcePerCardPlayed.add(effect)

        is CardEffect.GainCastlePerCardPlayed ->
            self.gainCastlePerCardPlayed.add(effect)

        is CardEffect.SwapHands -> {
            val selfOldHand     = self.hand.toList()
            val opponentOldHand = opponent.hand.toList()
            self.hand.clear()
            self.hand.addAll(opponentOldHand)
            opponent.hand.clear()
            opponent.hand.addAll(selfOldHand)
            // Zaloguj každou kartu z původní soupeřovy ruky jako ukradenou
            for (card in opponentOldHand) {
                onOpponentCardLost?.invoke(card, CardAction.STOLEN)
            }
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
    val validPool = pool.filter { tmpl -> tmpl.effects.none { it is CardEffect.ShapeShift } }
    if (validPool.isEmpty()) return
    for (i in hand.indices) {
        if (hand[i].isShapeShifterInstance()) {
            val tmpl = validPool.random()
            hand[i] = tmpl.copy(id = hand[i].id)  // id stále začíná "C34_" → příští kolo se znovu transformuje
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
