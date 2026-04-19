// ============================================================
// Condition.kt
// ============================================================
package com.example.termiti
sealed class Condition {
    data class ResourceAbove(val type: ResourceType, val threshold: Int) : Condition()
    data class WallAbove(val threshold: Int) : Condition()
    data class WallBelow(val threshold: Int) : Condition()
    data class CastleAbove(val threshold: Int) : Condition()
    data class CastleBelow(val threshold: Int) : Condition()
    /** Platí, pokud TATO karta (právě hraná) má daný typ (např. "Útok", "Stavba", "Důl", "Magie", "Chaos"). */
    data class LastPlayedType(val cardType: String) : Condition()
}