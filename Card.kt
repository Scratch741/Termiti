// Termiti/src/main/kotlin/model/Card.kt
data class Card(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val effects: List<CardEffect>
)

sealed class CardEffect {
    data class AddResource(val type: ResourceType, val amount: Int) : CardEffect()
    data class BuildWall(val amount: Int) : CardEffect()
    data class BuildCastle(val amount: Int) : CardEffect()
    data class AttackWall(val amount: Int) : CardEffect()
    data class AttackCastle(val amount: Int) : CardEffect()
    data class ConditionalEffect(val condition: Condition, val effect: CardEffect) : CardEffect()
}

sealed class Condition {
    data class ResourceAbove(val type: ResourceType, val threshold: Int) : Condition()
    data class WallAbove(val threshold: Int) : Condition()
    data class WallBelow(val threshold: Int) : Condition()
    data class CastleAbove(val threshold: Int) : Condition()
}

enum class ResourceType {
    MAGIC,
    ATTACK,
    STONES
}