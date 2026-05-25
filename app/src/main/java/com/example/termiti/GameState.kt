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
            // Limit kol: až po vyhodnocení normálních podmínek, spustí se po konci
            // 99. zobrazeného kola (hráč vidí "Kolo 99", tah se přičte na 100).
            // Pokud jsou oba balíčky prázdné, hra skončila vyčerpáním karet (ne časovým
            // limitem) → použij "Balíčky došly" zprávu, ne "Limit 99 kol".
            currentTurn > 99        -> if (playerState.deck.isEmpty() && aiState.deck.isEmpty())
                                           resolveByHp()
                                       else
                                           resolveByHpTurnLimit()
            else                    -> null
        }
    }

    /** Porovná hrady po vzájemném přeskočení kola s prázdnými balíčky. */
    fun resolveByHp(): GameResult = when {
        playerState.castleHP > aiState.castleHP -> GameResult.PLAYER_HP_WINS
        aiState.castleHP > playerState.castleHP -> GameResult.AI_HP_WINS
        else                                    -> GameResult.DRAW
    }

    /** Porovná hrady po dosažení limitu kol (99. kolo). */
    fun resolveByHpTurnLimit(): GameResult = when {
        playerState.castleHP > aiState.castleHP -> GameResult.PLAYER_HP_WINS_TURN_LIMIT
        aiState.castleHP > playerState.castleHP -> GameResult.AI_HP_WINS_TURN_LIMIT
        else                                    -> GameResult.DRAW
    }
}