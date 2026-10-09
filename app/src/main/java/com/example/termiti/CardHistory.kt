package com.example.termiti

/** Typ akce karty. */
enum class CardAction { PLAYED, DISCARDED, BURNED, STOLEN }

/** Jeden záznam v historii zahraných/zahozených/spálených karet (mini-pruh). */
data class CardHistoryEntry(val card: Card, val action: CardAction, val isMine: Boolean)

/** Jeden záznam v herním logu (overlay). */
sealed class LogEntry {
    /** Akce s konkrétní kartou (zahrání, zahození, spálení, ukradení). */
    data class CardEvent(
        val actorName : String,
        val card      : Card,
        val action    : CardAction,
        val isMe      : Boolean,   // true = akci provedl lokální hráč
        val turn      : Int = 0,
        /** Kolik se skutečně zaplatilo (u X-karet hodnota X). Null = neznámé → zobrazí se cena karty. */
        val paidCost  : Int? = null,
        /** Karta šla jako combo – vlastní combo, nebo ji combo udělala předchozí karta. */
        val asCombo   : Boolean = false,
        /** Karta shořela přelíznutím (plná ruka) – v logu „přelíznul", ne „spálil". */
        val overdraw  : Boolean = false
    ) : LogEntry()

    /**
     * Pasivní schopnost jedné ze stran – vypisuje se na začátku hry, aby bylo vidět,
     * co která strana dostala a co to dělá.
     */
    data class AbilityEvent(
        val ability     : PassiveAbility,
        val actorName   : String,   // "Hráč" / "AI" – přeloží se až při zobrazení (jako u CardEvent)
        val isMe        : Boolean,
        /** Drawable jméno avataru vlastníka (player_icon_*, enemy_icon_*, portrét soupeře v kampani). */
        val ownerAvatar : String
    ) : LogEntry()

    /** Systémová zpráva bez karty (přeskočení tahu, konec hry, apod.). */
    data class SystemEvent(val message: String) : LogEntry()
}
