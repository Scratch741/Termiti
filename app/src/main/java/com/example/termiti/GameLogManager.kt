package com.example.termiti

import androidx.compose.runtime.MutableState

/**
 * Sdílené extension funkce pro herní log a historii karet.
 * Používají: GameViewModel, MultiplayerViewModel, OnlineLobbyViewModel.
 */

/** Přidá systémovou zprávu do logu (keepuje posledních 50 záznamů). */
fun MutableState<List<LogEntry>>.appendLog(message: String) {
    value = (value + LogEntry.SystemEvent(message)).takeLast(50)
}

/** Přidá kartu do historie (max 20 záznamů, nejnovější první). */
fun MutableState<List<CardHistoryEntry>>.appendHistory(card: Card, action: CardAction, isMine: Boolean) {
    val h = value.toMutableList()
    h.add(0, CardHistoryEntry(card, action, isMine))
    if (h.size > 20) h.removeAt(h.size - 1)
    value = h
}
