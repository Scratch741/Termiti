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
        val playerDead  = playerState.castleHP <= 0
        val aiDead      = aiState.castleHP     <= 0
        val playerBuilt = playerState.castleHP >= 60
        val aiBuilt     = aiState.castleHP     >= 60
        return when {
            // Simultánní smrt / simultánní postavení → remíza (prohra pro oba)
            playerDead  && aiDead   -> GameResult.DRAW
            playerBuilt && aiBuilt  -> GameResult.DRAW
            playerDead              -> GameResult.PLAYER_CASTLE_DESTROYED
            aiDead                  -> GameResult.AI_CASTLE_DESTROYED
            playerBuilt             -> GameResult.PLAYER_CASTLE_BUILT
            aiBuilt                 -> GameResult.AI_CASTLE_BUILT
            else                    -> null
        }
    }

    /** Porovná hrady po vzájemném přeskočení kola s prázdnými balíčky. */
    fun resolveByHp(): GameResult = when {
        playerState.castleHP > aiState.castleHP -> GameResult.PLAYER_HP_WINS
        aiState.castleHP > playerState.castleHP -> GameResult.AI_HP_WINS
        else                                    -> GameResult.DRAW
    }
}