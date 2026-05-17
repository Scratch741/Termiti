// ============================================================
// GameState.kt
// ============================================================
package com.example.termiti
data class GameState(
    val playerState: PlayerState,
    val aiState: PlayerState,
    var currentTurn: Int = 1,
    var activePlayer: ActivePlayer = ActivePlayer.PLAYER,
    val playerWinTarget: Int = 70,  // zvýší se na 75 s pasivní schopností extra_castle
    val aiWinTarget: Int = 70,      // zvýší se na 75, pokud hráč má iron_bastion nebo AI má extra_castle
    val playerMaxHand: Int = 7,     // zvýší se na 8 s pasivní schopností extra_hand_card
    val aiMaxHand: Int = 7          // zvýší se na 8, pokud AI dostala extra_hand_card
) {
    fun checkWinCondition(): GameResult? {
        val playerDead  = playerState.castleHP <= 0
        val aiDead      = aiState.castleHP     <= 0
        val playerBuilt = playerState.castleHP >= playerWinTarget
        val aiBuilt     = aiState.castleHP     >= aiWinTarget
        return when {
            // Simultánní smrt / simultánní postavení → remíza (prohra pro oba)
            playerDead  && aiDead   -> GameResult.DRAW_BOTH_DEAD
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