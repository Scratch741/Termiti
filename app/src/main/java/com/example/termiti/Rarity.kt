package com.example.termiti

enum class Rarity(val label: String, val maxCopies: Int) {
    COMMON("Běžná", 4),
    RARE("Vzácná", 3),
    EPIC("Epická", 2),
    LEGENDARY("Legendární", 1)
}
