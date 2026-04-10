// ============================================================
// Condition.kt
// ============================================================
package com.example.termiti
sealed class Condition {
    data class ResourceAbove(val type: ResourceType, val threshold: Int) : Condition()
    data class WallAbove(val threshold: Int) : Condition()
    data class WallBelow(val threshold: Int) : Condition()
    data class CastleAbove(val threshold: Int) : Condition()
}