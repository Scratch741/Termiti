package com.example.termiti

// ─── Datové třídy ────────────────────────────────────────────────────────────

/** Jeden snímek replaye – stav hry po zahrání/zahození karty. */
data class ReplayFrame(
    val state      : GameState,
    val card       : Card?        = null,
    val isPlayer   : Boolean      = true,
    val action     : CardAction   = CardAction.PLAYED,
    val turnNumber : Int          = 1
)

/** Kompletní záznam jedné hry. */
data class GameReplay(
    val frames          : List<ReplayFrame>,
    val playerName      : String     = "Hráč",
    val playerAvatar    : String     = "player_icon_1",
    val opponentName    : String     = "Nepřítel",
    val opponentAvatar  : String     = "👺",
    val result          : GameResult,
    val playerWinTarget : Int        = 70,
    val aiWinTarget     : Int        = 70
)

// ─── Singleton úložiště ───────────────────────────────────────────────────────

/** Drží replay právě dohrané partie (přepíše se po každé hře). */
object ReplayManager {
    var lastReplay: GameReplay? = null
}

// ─── Helper ───────────────────────────────────────────────────────────────────

/** Deep-copy GameState pro záznam – zabrání mutaci uložených snímků. */
fun GameState.snapshot(): GameState =
    copy(playerState = playerState.deepCopy(), aiState = aiState.deepCopy())
