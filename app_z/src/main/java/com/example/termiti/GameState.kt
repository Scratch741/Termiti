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
            playerState.castleHP <= 0  -> GameResult.PLAYER_CASTLE_DESTROYED
            aiState.castleHP     <= 0  -> GameResult.AI_CASTLE_DESTROYED
            playerState.castleHP >= 60 -> GameResult.PLAYER_CASTLE_BUILT
            aiState.castleHP     >= 60 -> GameResult.AI_CASTLE_BUILT
            else -> null
        }
    }

    /** Porovná hrady po vzájemném přeskočení kola s prázdnými balíčky. */
    fun resolveByHp(): GameResult = when {
        playerState.castleHP > aiState.castleHP -> GameResult.PLAYER_HP_WINS
        aiState.castleHP > playerState.castleHP -> GameResult.AI_HP_WINS
        else                                    -> GameResult.DRAW
    }
}