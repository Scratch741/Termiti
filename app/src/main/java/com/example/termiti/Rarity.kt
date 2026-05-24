package com.example.termiti

enum class Rarity(
    val label      : String,
    val maxCopies  : Int,
    /** Prach získaný za rozmontování / duplikát v balíčku. */
    val dustValue  : Int,
    /** Cena craftu (výroby) 1 kopie z prachu. */
    val craftCost  : Int,
    /** Relativní váha při náhodném losování karty ze standardního slotu balíčku. */
    val packWeight : Int
) {
    COMMON    ("Běžná",       3,    5,   40,   0),   // základní karty – v balíčcích se nevyskytují
    RARE      ("Vzácná",      2,   20,  100,  75),
    EPIC      ("Epická",      2,  100,  400,  20),
    LEGENDARY ("Legendární",  1,  400, 1600,   5)
}
