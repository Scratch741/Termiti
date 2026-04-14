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
    onOpponentCardLost: ((Card, CardAction) -> Unit)? = null
) {
    for (effect in effects) when (effect) {
        is CardEffect.AddResource   ->
            self.resources[effect.type] = (self.resources[effect.type] ?: 0) + effect.amount

        is CardEffect.AddMine       ->
            self.mines[effect.type] = (self.mines[effect.type] ?: 0) + effect.amount

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
            self.resources[effect.type]     = (self.resources[effect.type]     ?: 0) + taken
        }

        is CardEffect.DrainResource -> {
            val drained = minOf(effect.amount, opponent.resources[effect.type] ?: 0)
            opponent.resources[effect.type] = (opponent.resources[effect.type] ?: 0) - drained
        }

        is CardEffect.ConditionalEffect ->
            if (checkCondition(effect.condition, self))
                applyEffects(listOf(effect.effect), self, opponent, allCards, onOpponentCardLost)

        is CardEffect.DestroyMine   -> {
            val cur = opponent.mines[effect.type] ?: 0
            if (cur > 0) opponent.mines[effect.type] = (cur - effect.amount).coerceAtLeast(0)
        }

        is CardEffect.StealCard     -> repeat(effect.count) {
            if (opponent.hand.isNotEmpty()) {
                val stolen = opponent.hand.random()
                opponent.hand.remove(stolen)
                self.hand.add(stolen)
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
                    self.deck.add(template.copy(id = "${template.id}_x${System.currentTimeMillis()}"))
                }
                self.deck.shuffle()
            }
        }

        is CardEffect.DrawCard ->
            self.drawCards(effect.count)   // přebytečné karty shoří (hand full → discardPile)

        is CardEffect.StealCastle -> {
            val stolen = minOf(effect.amount, opponent.castleHP.coerceAtLeast(0))
            opponent.castleHP -= stolen
            self.castleHP = (self.castleHP + stolen).coerceAtMost(100)
        }
    }
}

fun checkCondition(condition: Condition, player: PlayerState): Boolean = when (condition) {
    is Condition.ResourceAbove -> (player.resources[condition.type] ?: 0) > condition.threshold
    is Condition.WallAbove     -> player.wallHP   > condition.threshold
    is Condition.WallBelow     -> player.wallHP   < condition.threshold
    is Condition.CastleAbove   -> player.castleHP > condition.threshold
}
