package com.example.termiti

/**
 * Definice všech pasivních schopností.
 * Efekty se aplikují v [GameViewModel.createInitialState] na startovní [PlayerState].
 */
enum class PassiveAbility(
    val id:          String,
    val title:       String,
    val description: String,
    val icon:        String,
    val unlockLevel: Int,
    val goldCost:    Int
) {
    EXTRA_CASTLE(
        id          = "extra_castle",
        title       = "Pevný hrad",
        description = "+5 startovní hrad, ale vítězný cíl se zvýší z 60 na 65.",
        icon        = "🏰",
        unlockLevel = 2,
        goldCost    = 150
    ),
    EXTRA_WALL(
        id          = "extra_wall",
        title       = "Silné hradby",
        description = "+5 startovní hradba.",
        icon        = "🧱",
        unlockLevel = 3,
        goldCost    = 100
    ),
    EXTRA_MAGIC(
        id          = "extra_magic",
        title       = "Magický talent",
        description = "+1 magie na začátku hry.",
        icon        = "✨",
        unlockLevel = 4,
        goldCost    = 120
    ),
    EXTRA_ATTACK(
        id          = "extra_attack",
        title       = "Bojový výcvik",
        description = "+1 útok na začátku hry.",
        icon        = "⚔️",
        unlockLevel = 4,
        goldCost    = 120
    ),
    EXTRA_STONES(
        id          = "extra_stones",
        title       = "Kamenný základ",
        description = "+1 kameny na začátku hry.",
        icon        = "🪨",
        unlockLevel = 5,
        goldCost    = 120
    ),
    EXTRA_CHAOS(
        id          = "extra_chaos",
        title       = "Chaotická mysl",
        description = "+1 chaos na začátku hry.",
        icon        = "🌀",
        unlockLevel = 6,
        goldCost    = 150
    );

    companion object {
        fun fromId(id: String): PassiveAbility? = entries.find { it.id == id }

        /** Maximální počet aktivních schopností najednou. */
        const val MAX_ACTIVE = 2
    }
}
