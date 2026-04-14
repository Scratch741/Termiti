// ============================================================
// GameResult.kt
// ============================================================
package com.example.termiti
enum class GameResult {
    PLAYER_CASTLE_DESTROYED,
    AI_CASTLE_DESTROYED,
    PLAYER_CASTLE_BUILT,
    AI_CASTLE_BUILT,
    /** Oba balíčky vyčerpány; hráč má větší hrad. */
    PLAYER_HP_WINS,
    /** Oba balíčky vyčerpány; AI má větší hrad. */
    AI_HP_WINS,
    /** Oba balíčky vyčerpány; hrady jsou stejně vysoké. */
    DRAW,
}

fun GameResult.isPlayerWin() = this == GameResult.AI_CASTLE_DESTROYED
                             || this == GameResult.PLAYER_CASTLE_BUILT
                             || this == GameResult.PLAYER_HP_WINS
