// ============================================================
// CardEffect.kt
// ============================================================
package com.example.termiti

sealed class CardEffect {
    data class AddResource(val type: ResourceType, val amount: Int) : CardEffect()
    data class AddMine(val type: ResourceType, val amount: Int = 1) : CardEffect()
    data class BuildWall(val amount: Int) : CardEffect()
    data class BuildCastle(val amount: Int) : CardEffect()
    /** Útočí na hráče: nejdřív poškodí hradby, přebytek jde na hrad. */
    data class AttackPlayer(val amount: Int) : CardEffect()
    /** Specializovaný útok POUZE na hradby – žádné přetečení na hrad. */
    data class AttackWall(val amount: Int) : CardEffect()
    /** Přímý zásah hradu – ignoruje hradby. */
    data class AttackCastle(val amount: Int) : CardEffect()
    /** Ukradne zdroj od protivníka a přidá ho sobě. */
    data class StealResource(val type: ResourceType, val amount: Int) : CardEffect()
    /** Zničí zdroj protivníka (bez zisku pro hráče). */
    data class DrainResource(val type: ResourceType, val amount: Int) : CardEffect()
    data class ConditionalEffect(val condition: Condition, val effect: CardEffect) : CardEffect()
    /** Sníží těžbu soupeře daného typu o amount (min 0). */
    data class DestroyMine(val type: ResourceType, val amount: Int = 1) : CardEffect()
    /** Ukradne count náhodných karet ze soupeřovy ruky do vlastní ruky. */
    data class StealCard(val count: Int = 1) : CardEffect()
    /** Zničí count náhodných karet ze soupeřovy ruky (odejdou do jeho odpadního balíčku). */
    data class BurnCard(val count: Int = 1) : CardEffect()
    /** Přidá count kopií karty s daným id do vlastního balíčku (zamíchá). */
    data class AddCardsToDeck(val cardId: String, val count: Int = 1) : CardEffect()
    /** Líže count karet z vlastního balíčku do ruky (přebytečné shoří). */
    data class DrawCard(val count: Int = 1) : CardEffect()
    /** Ukradne amount životů hradu soupeři a přidá je vlastnímu hradu. */
    data class StealCastle(val amount: Int) : CardEffect()
}
