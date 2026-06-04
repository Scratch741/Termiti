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
    val endCombo: String,
    val waitingTurn: String,
    val inspectGame: String,
    val gameLog: String,
    val enemy: String,
    val viewOpponentHand: String,
    val viewMyHand: String,
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
    val decisionDrawFromDeck: String,
    val decisionMine: String,
    val decisionSmartJoker: String,
    val decisionPeekTitle: String,
    val decisionPeekSubtitle: String,
    val decisionAlchemyTitle: String,
    val decisionAlchemySubtitle: String,
    val decisionResourceCardDesc: String,   // "Přidá %d %s do tvých surovin." (amount, resource)

    // ── Game results ─────────────────────────────────────────────────────────
    val resultVictory: String,
    val resultDefeat: String,
    val resultDraw: String,
    val resultCastleBuilt: String,
    val resultCastleBuiltOpponent: String,
    val resultCastleDestroyed: String,
    val resultCastleDestroyedSelf: String,
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
    val deckDefaultName: String,          // %d = slot number
    val dbActiveShort: String,
    val dbSearchHint: String,
    val dbEffectLabel: String,
    val catAttack: String,
    val catDefense: String,
    val catResources: String,
    val catMines: String,
    val catCombo: String,
    val catDecision: String,
    val catOther: String,
    val dbFilterUnlocked: String,
    val dbDisassemble: String,            // %d = dust value
    val dbDustGain: String,               // %d = dust
    val dbDustCost: String,               // %d = dust
    val dbBadgeNew: String,
    val dbTemplates: String,
    val dbSetActive: String,
    val dbActiveDeck: String,
    val dbComposition: String,
    val dbConfirm: String,
    val dbDone: String,

    // ── Arena ────────────────────────────────────────────────────────────────
    val arenaDraft: String,
    val arenaPickCard: String,
    val arenaWins: String,                // String.format(s.arenaWins, count)

    // ── Mulligan ─────────────────────────────────────────────────────────────
    val mulliganTitle: String,
    val mulliganSubtitle: String,
    val mulliganConfirm: String,
    val mulliganYouFirst: String,
    val mulliganOpponentFirst: String,
    val mulliganWaitingOpponent: String,
    val mulliganInstruction: String,
    val mulliganSelected: String,       // formát s %d (počet vybraných karet)
    val mulliganPlayNoSwap: String,
    val mulliganSwap: String,

    // ── Profile ──────────────────────────────────────────────────────────────
    val profileTitle: String,
    val profileWins: String,
    val profileLosses: String,
    val profileGames: String,
    val profileLevel: String,             // %d
    val profileGold: String,
    val profileGems: String,
    val profilePlayed: String,
    val profileUnlockAll: String,
    val profileSectionAvatar: String,
    val profileSectionCastle: String,
    val profileSectionCardBack: String,
    val profileSectionAbilities: String,
    val profileActiveCount: String,       // %d / %d
    val profileSectionCosmetics: String,
    val profileCosmeticsSoon: String,
    val profileActive: String,
    val toggleOn: String,
    val toggleOff: String,
    val slotFull: String,
    val castleClassic: String,
    val castleStone: String,
    val castleDark: String,
    val cardBackBasic: String,
    val cardBackStyle2: String,
    val cardBackStyle3: String,
    val questsTitle: String,
    val questsReset: String,
    val questClaim: String,
    val questWinGames: String,      // %d
    val questWinOnline: String,     // %d
    val questPlayCards: String,     // %d
    val questDealDamage: String,    // %d
    val questWinCampaign: String,   // %d

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

    // ── Resource names (battlefield HUD) ───────────────────────────────────────
    val resMagic: String,
    val resAttack: String,
    val resStone: String,
    val resChaos: String,

    // ── Game log feed (bottom-right) ───────────────────────────────────────────
    // Card-event row: actor + verb
    val logActorPlayer: String,
    val logActorAi: String,
    val logVerbPlayed: String,
    val logVerbDiscarded: String,
    val logVerbBurned: String,
    val logVerbStolen: String,
    // System events (some take %s = card/resource, %d = amount — sequential order)
    val logBurnedFromOppDeck: String,
    val logChose: String,
    val logTookFromDiscard: String,
    val logCopiedFromDeck: String,
    val logDrewFromDeck: String,
    val logChoseMine: String,
    val logJoker: String,
    val logStoleFromHand: String,
    val logChoseResource: String,
    val logPlayerFirst: String,
    val logAiFirst: String,
    val logNotEnough: String,
    val logConditionNotMet: String,
    val logReplication: String,
    val logPlayerEndTurn: String,
    val logPlayerSkip: String,
    val logAiDiscardFromDeck: String,
    val logAiJoker: String,
    val logAiStoleFromHand: String,
    val logAiChoseResource: String,
    val logAiWaited: String,
    val logBothPassedEmpty: String,
    val logBothNoCards: String,
    // Trap-on-draw message: "💥 <who> <card>! <effect>"
    val logTrapDrewYou: String,
    val logTrapDrewAi: String,
    val logTrapCastle: String,
    val logTrapWall: String,
    val logTrapHp: String,
    val logTrapTriggered: String,
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
