// ============================================================
// AppStrings.kt
// ============================================================
package com.example.termiti

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * All user-visible UI strings in one place.
 *
 * Instances are created by [LanguagePack.fromJson] — never construct manually.
 *
 * HOW TO USE in a Composable:
 *   val s = LocalStrings.current
 *   Text(s.settings)
 *
 * HOW TO ADD a new string:
 *   1. Add the property here
 *   2. Add the key to LanguagePack.buildStrings (with CZ fallback)
 *   3. Add the key to assets/lang/cs.json and assets/lang/en.json
 *   Done — community translators only touch the JSON files.
 */
data class AppStrings(
    val languageCode: String,

    // ── General ──────────────────────────────────────────────────────────────
    val ok: String,
    val cancel: String,
    val confirm: String,
    val back: String,

    // ── Settings ─────────────────────────────────────────────────────────────
    val settings: String,
    val music: String,
    val soundEffects: String,
    val languageLabel: String,

    // ── Main menu ────────────────────────────────────────────────────────────
    val play: String,
    val buildDeck: String,
    val multiplayer: String,
    val profile: String,
    val shop: String,
    val exit: String,

    // ── Play menu ────────────────────────────────────────────────────────────
    val ownDeck: String,
    val superRandom: String,
    val arena: String,
    val campaign: String,

    // ── Game HUD ─────────────────────────────────────────────────────────────
    val yourTurn: String,
    val opponentTurn: String,
    val endTurn: String,
    val discard: String,
    val castle: String,
    val wall: String,
    val round: String,

    // ── Decision overlay ─────────────────────────────────────────────────────
    val decisionTitle: String,
    val decisionPreviewGame: String,
    val decisionBackToDecision: String,
    val decisionChooseType: String,       // use String.format(s.decisionChooseType, typeName)
    val decisionBurnOpponent: String,
    val decisionFromDiscard: String,
    val decisionFromDeck: String,
    val decisionMine: String,

    // ── Game results ─────────────────────────────────────────────────────────
    val resultVictory: String,
    val resultDefeat: String,
    val resultDraw: String,
    val resultCastleBuilt: String,
    val resultCastleDestroyed: String,
    val resultHpWins: String,
    val resultHpLose: String,
    val resultHpWinsTurnLimit: String,
    val resultHpLoseTurnLimit: String,
    val resultBothDead: String,
    val resultPlayAgain: String,
    val resultBackToMenu: String,

    // ── Deck builder ─────────────────────────────────────────────────────────
    val deckBuilder: String,
    val deckSave: String,
    val deckReset: String,
    val deckClear: String,
    val deckCardCount: String,            // String.format(s.deckCardCount, current, max)

    // ── Arena ────────────────────────────────────────────────────────────────
    val arenaDraft: String,
    val arenaPickCard: String,
    val arenaWins: String,                // String.format(s.arenaWins, count)

    // ── Mulligan ─────────────────────────────────────────────────────────────
    val mulliganTitle: String,
    val mulliganSubtitle: String,
    val mulliganConfirm: String,

    // ── Profile ──────────────────────────────────────────────────────────────
    val profileTitle: String,
    val profileWins: String,
    val profileLosses: String,
    val profileGames: String,

    // ── Shop ─────────────────────────────────────────────────────────────────
    val shopTitle: String,
    val shopBuy: String,
    val shopDust: String,

    // ── Online ───────────────────────────────────────────────────────────────
    val onlineConnecting: String,
    val onlineWaiting: String,
    val onlineDisconnected: String,

    // ── Rarities ─────────────────────────────────────────────────────────────
    val rarityCommon: String,
    val rarityRare: String,
    val rarityEpic: String,
    val rarityLegendary: String,

    // ── Card types ───────────────────────────────────────────────────────────
    val typeAttack: String,
    val typeBuild: String,
    val typeMagic: String,
    val typeChaos: String,
    val typeMines: String,
    val typeDecision: String,
    val typeDraw: String,
)

// ── CompositionLocal ──────────────────────────────────────────────────────────

/** Provides the current [AppStrings] to the Compose tree. Set up in MainActivity. */
val LocalStrings = compositionLocalOf<AppStrings> {
    // Emergency fallback — should never be reached if MainActivity sets the provider correctly
    LanguagePack.fallback().strings
}

/** Shortcut: read current strings inside any Composable. */
val currentStrings: AppStrings
    @Composable
    @ReadOnlyComposable
    get() = LocalStrings.current
