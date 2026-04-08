// Termiti/src/main/kotlin/Game.kt
import model.*

class Game(private val playerDeck: List<Card>, private val aiDeck: List<Card>) {
    private var gameState = GameState(
        playerState = PlayerState().apply { deck.addAll(playerDeck) },
        aiState = PlayerState().apply { deck.addAll(aiDeck) }
    )

    fun startGame() {
        while (true) {
            // Draw 5 cards at the beginning of each turn
            gameState.playerState.drawCards(5)
            gameState.aiState.drawCards(5)

            // Player's turn
            gameState.activePlayer = ActivePlayer.PLAYER
            if (!playTurn(gameState.playerState)) break

            // AI's turn
            gameState.activePlayer = ActivePlayer.AI
            if (!playTurn(gameState.aiState)) break

            // Check win condition
            val result = gameState.checkWinCondition()
            if (result != null) {
                println("Game Over! Result: $result")
                break
            }
        }
    }

    private fun playTurn(playerState: PlayerState): Boolean {
        // For simplicity, let's assume the player plays their first card in hand
        val cardToPlay = playerState.hand.firstOrNull()
        if (cardToPlay != null && playerState.playCard(cardToPlay)) {
            println("Played card: ${cardToPlay.name}")
        } else {
            println("Player cannot play any cards or deck is empty.")
            return false
        }
        return true
    }
}