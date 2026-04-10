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
            // Oba balíčky vyčerpány → rozhodne výška hradu.
            // Ruce záměrně nehlídáme: AI hraje jen 1 kartu za tah, takže kdyby se čekalo
            // na prázdné ruce, hra by pokračovala zbytečně mnoho kol.
            playerState.deck.isEmpty() && aiState.deck.isEmpty() -> when {
                playerState.castleHP > aiState.castleHP -> GameResult.PLAYER_HP_WINS
                aiState.castleHP > playerState.castleHP -> GameResult.AI_HP_WINS
                else                                    -> GameResult.DRAW
            }
            else -> null
        }
    }
}