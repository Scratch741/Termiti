// ============================================================
// CardEffect.kt
// ============================================================
package com.example.termiti

/** Jedna možnost volby zdroje (použito v [CardEffect.DecisionChooseResource]). */
data class ResourceOption(val type: ResourceType, val amount: Int)

sealed class CardEffect {
    data class AddResource(val type: ResourceType, val amount: Int) : CardEffect()
    /** Přidá suroviny až příští kolo (turns = 1 → příští tah, 2 → přespříští…). */
    data class AddResourceDelayed(val type: ResourceType, val amount: Int, val turns: Int = 1) : CardEffect()
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
    /** Sníží těžbu soupeře daného typu o amount (min 1 – nelze zničit poslední důl). */
    data class DestroyMine(val type: ResourceType, val amount: Int = 1) : CardEffect()
    /** Zablokuje produkci soupeřova dolu daného typu na [turns] kol. */
    data class BlockMine(val type: ResourceType, val turns: Int) : CardEffect()
    /** Ukradne count náhodných karet ze soupeřovy ruky do vlastní ruky. */
    data class StealCard(val count: Int = 1) : CardEffect()
    /** Zničí count náhodných karet ze soupeřovy ruky (odejdou do jeho odpadního balíčku). */
    data class BurnCard(val count: Int = 1) : CardEffect()
    /** Přidá count kopií karty s daným id do vlastního balíčku (zamíchá). */
    data class AddCardsToDeck(val cardId: String, val count: Int = 1) : CardEffect()
    /** Přidá count kopií karty s daným id do balíčku SOUPEŘE (zamíchá). */
    data class AddToOpponentDeck(val cardId: String, val count: Int = 1) : CardEffect()
    /** Pasca: při líznutí (ne při zahraní) se ihned spustí [effect] na hráče, který lízl, a karta je zahozena. */
    data class TrapOnDraw(val effect: CardEffect) : CardEffect()
    /** Líže count karet z vlastního balíčku do ruky (přebytečné shoří). */
    data class DrawCard(val count: Int = 1) : CardEffect()
    /** Oba hráči líží count karet (přebytečné shoří). */
    data class DrawBoth(val count: Int = 1) : CardEffect()
    /**
     * Toto kolo: příští zahraná karta bude naklonována — [count] kopií se zamíchá do balíčku.
     * Resetuje se při přechodu na nový tah nebo po aktivaci.
     */
    data class CloneNextPlayed(val count: Int = 2) : CardEffect()
    /** Ukradne amount životů hradu soupeři a přidá je vlastnímu hradu. */
    data class StealCastle(val amount: Int) : CardEffect()
    /** Prohodí celé ruce hráčů – self dostane ruku soupeře a naopak. */
    object SwapHands : CardEffect()
    /**
     * Velký zmatek – oba hráči zahodí celé ruce a líznout stejný počet nových karet ze svého balíčku.
     */
    object RandomizeHands : CardEffect()
    /**
     * Přidá 1 náhodnou kartu daného [costType] přímo do ruky hráče (isGenerated = true).
     * Karta se vybírá z celého poolu — bez decision obrazovky.
     */
    data class GiveRandomCard(val costType: ResourceType) : CardEffect()
    /**
     * Změní [costModifier] na všech kartách v ruce o [delta].
     * [targetOpponent] = false → vlastní ruka, true → soupeřova ruka.
     * Výsledný effectiveCost je vždy omezen na 0–99 (viz Card.effectiveCost).
     */
    data class ModifyHandCost(val delta: Int, val targetOpponent: Boolean = false) : CardEffect()
    /** Aktivuje efekt: za každou DALŠÍ zahranou kartu v tomto kole líznout 1 kartu.
     *  [cardType] = null → libovolný typ; neprázdný řetězec → jen daný typ (např. "Magie"). */
    data class DrawPerCardPlayed(val cardType: String? = null) : CardEffect()
    /**
     * Aktivuje efekt: za každou DALŠÍ zahranou kartu v tomto kole přidat [amount] zdroje [type].
     * [cardType] = null → triggeruje na jakoukoliv kartu; jinak jen na karty daného typu (např. "Útok").
     */
    data class GainResourcePerCardPlayed(
        val type: ResourceType,
        val amount: Int,
        val cardType: String? = null
    ) : CardEffect()
    /**
     * Aktivuje efekt: za každou DALŠÍ zahranou kartu v tomto kole přidat [amount] HP hradu.
     * [cardType] = null → triggeruje na jakoukoliv kartu; jinak jen na karty daného typu (např. "Stavba").
     */
    data class GainCastlePerCardPlayed(
        val amount: Int,
        val cardType: String? = null
    ) : CardEffect()
    /**
     * Persistentní efekt: na začátku každého tahu hráče se karta v ruce automaticky
     * přemění v náhodnou kartu ze hry (jiného typu než ShapeShift).
     * Karta se sama nikdy nehraje — vždy se transformuje dříve, než hráč hraje.
     */
    object ShapeShift : CardEffect()

    /**
     * Převede 1 jednotku dolu [from] na 1 jednotku dolu [to].
     * Netýká se soupeře – operuje výhradně na vlastních dolech.
     * Respektuje floor pro non-CHAOS doly (min 1) – nelze snížit z 1 → 0.
     */
    data class ConvertMine(val from: ResourceType, val to: ResourceType) : CardEffect()

    // ── Rozhodnutí ────────────────────────────────────────────────────────────
    /** Zobrazí [picks] karet ze soupeřova balíčku; hráč vybere jednu k zahození. */
    data class DecisionBurnOpponent(val picks: Int = 4) : CardEffect()
    /**
     * Zobrazí [picks] náhodných karet daného [cardType] ze hry; hráč přidá jednu do ruky.
     * [costReduction] = o kolik bude vybraná karta levnější (0 = žádná sleva).
     */
    data class DecisionChooseType(val cardType: String, val picks: Int = 4, val costReduction: Int = 0) : CardEffect()
    /** Zobrazí [picks] karet z vlastního odhazovacího balíčku; hráč přidá jednu do ruky. */
    data class DecisionFromDiscard(val picks: Int = 4) : CardEffect()
    /** Zobrazí [picks] karet z vlastního balíčku; hráč přidá jednu do ruky. */
    data class DecisionFromDeck(val picks: Int = 4) : CardEffect()
    /**
     * Zobrazí přesně 4 možnosti: 1 náhodný důl každého typu (Magie, Útok, Kámen, Chaos).
     * Hráč vybere jednu kartu a ta mu přijde do ruky.
     */
    object DecisionMine : CardEffect()
    /**
     * Zobrazí přesně 4 karty — po jedné z každého typu (Magie, Útok, Stavba, Chaos).
     * Karty jsou vybrány chytře na základě aktuální herní situace (HP hradu, suroviny, doly…).
     * Hráč si vybere jednu, která mu přijde do ruky.
     */
    object SmartJoker : CardEffect()
    /**
     * Útočí na hráče za [base] + ([bonusPerAttack] × počet útočných karet zahraných v tomto tahu).
     * Počítadlo je v [PlayerState.attackCardsThisTurn] a spravuje ho GameViewModel.
     */
    data class MomentumAttack(val base: Int, val bonusPerAttack: Int) : CardEffect()
    /**
     * Decision: zobrazí celou ruku soupeře; hráč si vybere jednu kartu a ukradne ji.
     */
    object PeekAndStealHand : CardEffect()
    /**
     * Zkopíruje efekty poslední karty zahrané soupeřem ([PlayerState.lastPlayedCard]).
     * Pokud soupeř ještě nic nezahrál, přidá 2 magie (fallback).
     * Vizuálně se karta v ruce přizpůsobuje naposledy zahrané soupeřově kartě
     * (art, popis) prostřednictvím [updateMirrorCards].
     */
    object Mirror : CardEffect()
    /**
     * Zobrazí 3 tlačítka volby zdroje; hráč si vybere jeden a obdrží daný počet surovin.
     * [options] = seznam možností (každá s typem zdroje a množstvím).
     */
    data class DecisionChooseResource(val options: List<ResourceOption>) : CardEffect()

    // ── X-kost efekty ─────────────────────────────────────────────────────────
    /** Poškodí hráče za (X / divisor) kde X = veškerý spotřebovaný zdroj při zahraní karty. */
    data class XScaledAttackPlayer(val divisor: Int = 2) : CardEffect()
    /** Přímý zásah hradu za (X / divisor) – ignoruje hradby. X = veškerý spotřebovaný zdroj. */
    data class XScaledAttackCastle(val divisor: Int = 2) : CardEffect()
    /** Opraví hrad o (X / divisor) kde X = veškerý spotřebovaný zdroj. */
    data class XScaledBuildCastle(val divisor: Int = 2) : CardEffect()
    /** Přidá (X / divisor) k oběma zadaným zdrojům kde X = veškerý spotřebovaný zdroj. */
    data class XScaledDualResource(val typeA: ResourceType, val typeB: ResourceType, val divisor: Int = 2) : CardEffect()
}
