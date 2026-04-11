package com.example.termiti

/** Typ akce karty. */
enum class CardAction { PLAYED, DISCARDED, BURNED, STOLEN }

/** Jeden záznam v historii zahraných/zahozených/spálených karet. */
data class CardHistoryEntry(val card: Card, val action: CardAction, val isMine: Boolean)
