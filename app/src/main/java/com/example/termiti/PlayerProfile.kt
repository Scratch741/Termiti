package com.example.termiti

/**
 * Hráčský profil – veškerý progress, měna a kosmetika.
 * Serializuje se do JSON a ukládá v SharedPreferences.
 */
data class PlayerProfile(
    val name: String,

    // ── Avatar ────────────────────────────────────────────────────────────────
    val avatar: String = "⚔️",  // emoji ID z PlayerAvatar

    // ── Progress ─────────────────────────────────────────────────────────────
    val level: Int = 1,
    val xp: Int = 0,           // aktuální XP v rámci levelu

    // ── Měna ─────────────────────────────────────────────────────────────────
    val gold: Int = 0,         // otevírání balíčků / schopností
    val gems: Int = 0,         // kosmetika (cardback, hrad, zeď …)

    // ── Statistiky ───────────────────────────────────────────────────────────
    val winsOffline: Int = 0,
    val winsOnline: Int = 0,
    val totalGames: Int = 0,

    // ── Pasivní schopnosti ────────────────────────────────────────────────────
    val unlockedAbilities: Set<String> = emptySet(),   // koupené ability IDs
    val activeAbilities:   List<String> = emptyList()  // max 2 aktivní (může být méně)
) {
    /** Kolik XP je potřeba pro přechod z [level] na [level]+1. */
    fun xpNeeded(): Int = xpForLevel(level)

    /** Celkový XP potřebný k dosažení daného levelu od nuly. */
    companion object {
        fun xpForLevel(level: Int): Int = level * 100   // 1→2: 100, 2→3: 200 …

        /**
         * Odměny za výsledek hry.
         * Vrátí trojici (xp, gold, gems).
         */
        fun rewardForResult(win: Boolean, online: Boolean): Triple<Int, Int, Int> = when {
            win && online  -> Triple(50, 25, 0)
            win && !online -> Triple(25, 10, 0)
            !win && online -> Triple(10,  5, 0)
            else           -> Triple( 5,  2, 0)   // prohra offline
        }
    }
}
