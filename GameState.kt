// Termiti/src/main/kotlin/model/GameState.kt
data class GameState(
    val playerState: PlayerState,
    val aiState: PlayerState,
    var currentTurn: Int = 0,
    var activePlayer: ActivePlayer = ActivePlayer.PLAYER
) {
    fun checkWinCondition(): GameResult? {
        return when {
            playerState.castleHp <= 0 -> GameResult.PLAYER_CASTLE_DESTROYED
            aiState.castleHp <= 0 -> GameResult.AI_CASTLE_DESTROYED
            playerState.castleHp >= 100 -> GameResult.PLAYER_CASTLE_BUILT
            aiState.castleHp >= 100 -> GameResult.AI_CASTLE_BUILT
            playerState.deck.isEmpty() && aiState.deck.isEmpty() -> GameResult.DECK_EXHAUSTED
            else -> null
        }
    }
}