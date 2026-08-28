package com.example.termiti

import kotlinx.serialization.Serializable

/**
 * Hráčský profil – veškerý progress, měna a kosmetika.
 * Serializuje se do JSON a ukládá v SharedPreferences.
 */
@Serializable
data class PlayerProfile(
    val name: String,

    // ── Avatar ────────────────────────────────────────────────────────────────
    val avatar: String = "player_icon_1",

    // ── Skin hradu ────────────────────────────────────────────────────────────
    val castleSkin: String = "castle_player",      // ID drawablu hradu hráče

    // ── Skin hradby ───────────────────────────────────────────────────────────
    val wallSkin: String = "wall_player",          // ID drawablu hradby hráče

    // ── Skin rubu karty ───────────────────────────────────────────────────────
    val cardBackSkin: String = "card_back_frame",  // ID drawablu rubu karty

    // ── Progress ─────────────────────────────────────────────────────────────
    val level: Int = 1,
    val xp: Int = 0,           // aktuální XP v rámci levelu

    // ── Měna ─────────────────────────────────────────────────────────────────
    val gold: Int = 0,         // otevírání balíčků / schopností
    val gems: Int = 0,         // kosmetika (cardback, hrad, zeď …)

    // ── Statistiky ───────────────────────────────────────────────────────────
    val winsOffline: Int = 0,
    val winsOnline: Int = 0,
    val totalGames: Int = 0,

    // ── Pasivní schopnosti ────────────────────────────────────────────────────
    val unlockedAbilities: Set<String> = emptySet(),   // koupené ability IDs
    val activeAbilities:   List<String> = emptyList(), // max 2 aktivní (může být méně)

    // ── Kolekce karet ─────────────────────────────────────────────────────────
    /** Počet vlastněných kopií každé karty: cardId → počet. */
    val cardCollection: Map<String, Int> = emptyMap(),
    /** Magický prach — měna na výrobu (crafting) karet. */
    val dust: Int = 0,
    /**
     * Karty, které hráč již viděl v deck builderu — použito pro zvýraznění nově
     * získaných karet značkou "NOVÉ". Karta je přidána při prvním kliknutí na náhled.
     */
    val seenCards: Set<String> = emptySet(),
    /**
     * Přepínač pro testování / debug: true = hráč může použít všechny karty
     * bez ohledu na skutečnou kolekci.
     * Výchozí false — aktivovat ručně přes ProfileScreen nebo CardCollectionManager.setAllCardsUnlocked(true).
     */
    val allCardsUnlocked: Boolean = false
) {
    /** Kolik XP je potřeba pro přechod z [level] na [level]+1. */
    fun xpNeeded(): Int = xpForLevel(level)

    /** Celkový XP potřebný k dosažení daného levelu od nuly. */
    companion object {
        fun xpForLevel(level: Int): Int = when {
            level <= 5  -> 150
            level <= 15 -> 350
            level <= 30 -> 550
            else        -> 750
        }

        /**
         * Odměny za výsledek hry.
         * Vrátí trojici (xp, gold, gems).
         */
        fun rewardForResult(win: Boolean, online: Boolean): Triple<Int, Int, Int> = when {
            win && online  -> Triple(50, 25, 0)
            win && !online -> Triple(25, 10, 0)
            !win && online -> Triple(10,  5, 0)
            else           -> Triple( 5,  2, 0)   // prohra offline
        }
    }
}
