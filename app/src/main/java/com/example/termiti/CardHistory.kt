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
        val turn      : Int = 0
    ) : LogEntry()

    /** Systémová zpráva bez karty (přeskočení tahu, konec hry, apod.). */
    data class SystemEvent(val message: String) : LogEntry()
}
