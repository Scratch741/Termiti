// ============================================================
// GameState.kt
// ============================================================
package com.example.termiti
data class GameState(
    val playerState: PlayerState,
    val aiState: PlayerState,
    var currentTurn: Int = 0,
    var activePlayer: ActivePlayer = ActivePlayer.PLAYER
) {
    fun checkWinCondition(): GameResult? {
        return when {
            playerState.castleHP <= 0 -> GameResult.PLAYER_CASTLE_DESTROYED
            aiState.castleHP <= 0 -> GameResult.AI_CASTLE_DESTROYED
            playerState.castleHP >= 60 -> GameResult.PLAYER_CASTLE_BUILT
            aiState.castleHP >= 60 -> GameResult.AI_CASTLE_BUILT
            playerState.deck.isEmpty() && aiState.deck.isEmpty() -> GameResult.DECK_EXHAUSTED
            else -> null
        }
    }
}