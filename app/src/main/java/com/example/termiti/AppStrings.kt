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
 *   1. Add the property here (`val myKey: String by values`)
 *   2. Add the key to LanguagePack.buildStrings (with CZ fallback)
 *   3. Add the key to assets/lang/cs.json and assets/lang/en.json
 *   Done — community translators only touch the JSON files.
 */
class AppStrings(
    val languageCode: String,
    /**
     * key -> text. Každá vlastnost níže se čte delegací `by values` (název vlastnosti
     * = klíč v JSON). Záměrně NE konstruktor s parametrem pro každý text: ART odmítne
     * volání s více než 255 argumentovými registry (VerifyError při startu), a textů
     * je přes 390.
     */
    private val values: Map<String, String>
) {
    /** Text pro [key], nebo null – používá fallback v [LanguagePack]. */
    operator fun get(key: String): String? = values[key]

    // ── General ──────────────────────────────────────────────────────────────
    val ok: String by values
    val cancel: String by values
    val confirm: String by values
    val back: String by values

    // ── Settings ─────────────────────────────────────────────────────────────
    val settings: String by values
    val music: String by values
    val soundEffects: String by values
    val languageLabel: String by values

    // ── Main menu ────────────────────────────────────────────────────────────
    val play: String by values
    val buildDeck: String by values
    val multiplayer: String by values
    val profile: String by values
    val shop: String by values
    val exit: String by values

    // ── Play menu ────────────────────────────────────────────────────────────
    val ownDeck: String by values
    val superRandom: String by values
    val arena: String by values
    val campaign: String by values

    // ── Game HUD ─────────────────────────────────────────────────────────────
    val yourTurn: String by values
    val opponentTurn: String by values
    val endTurn: String by values
    val endCombo: String by values
    val endGame: String by values
    val waitingTurn: String by values
    val inspectGame: String by values
    val gameLog: String by values
    val enemy: String by values
    val viewOpponentHand: String by values
    val viewMyHand: String by values
    val discard: String by values
    val castle: String by values
    val wall: String by values
    val round: String by values

    // ── Decision overlay ─────────────────────────────────────────────────────
    val decisionTitle: String by values
    val decisionPreviewGame: String by values
    val decisionBackToDecision: String by values
    val decisionChooseType: String by values       // use String.format(s.decisionChooseType, typeName)
    val decisionBurnOpponent: String by values
    val decisionFromDiscard: String by values
    val decisionFromDeck: String by values
    val decisionDrawFromDeck: String by values
    val decisionMine: String by values
    val decisionSmartJoker: String by values
    val decisionPeekTitle: String by values
    val decisionPeekSubtitle: String by values
    val decisionAlchemyTitle: String by values
    val decisionAlchemySubtitle: String by values
    val decisionResourceCardDesc: String by values   // "Přidá %d %s do tvých surovin." (amount, resource)

    // ── Game results ─────────────────────────────────────────────────────────
    val resultVictory: String by values
    val resultDefeat: String by values
    val resultDraw: String by values
    val resultCastleBuilt: String by values
    val resultCastleBuiltOpponent: String by values
    val resultCastleDestroyed: String by values
    val resultCastleDestroyedSelf: String by values
    val resultHpWins: String by values
    val resultHpLose: String by values
    val resultHpDraw: String by values
    val resultHpWinsTurnLimit: String by values
    val resultHpLoseTurnLimit: String by values
    val resultBothDead: String by values
    val resultPlayAgain: String by values
    val resultBackToMenu: String by values

    // ── Campaign result screen ───────────────────────────────────────────────
    val campaignVictory: String by values
    val campaignDefeat: String by values
    val campaignRewardFirstKill: String by values
    val campaignRewardAlreadyClaimed: String by values
    val campaignRetry: String by values
    val campaignBackToLocation: String by values
    val campaignNextOpponent: String by values

    // ── Deck builder ─────────────────────────────────────────────────────────
    val deckBuilder: String by values
    val deckSave: String by values
    val deckReset: String by values
    val deckClear: String by values
    val deckCardCount: String by values            // String.format(s.deckCardCount, current, max)
    val deckDefaultName: String by values          // %d = slot number
    val dbActiveShort: String by values
    val dbSearchHint: String by values
    val dbEffectLabel: String by values
    val catAttack: String by values
    val catDefense: String by values
    val catResources: String by values
    val catMines: String by values
    val catCombo: String by values
    val catDecision: String by values
    val catOther: String by values
    val dbFilterUnlocked: String by values
    val dbDisassemble: String by values            // %d = dust value
    val dbDustGain: String by values               // %d = dust
    val dbDustCost: String by values               // %d = dust
    val dbBadgeNew: String by values
    val dbTemplates: String by values
    val dbSetActive: String by values
    val dbActiveDeck: String by values
    val dbComposition: String by values
    val dbInDeck: String by values
    val dbConfirm: String by values
    val dbDone: String by values

    // ── Arena ────────────────────────────────────────────────────────────────
    val arenaDraft: String by values
    val arenaPickCard: String by values
    val arenaWins: String by values                // String.format(s.arenaWins, count)

    // ── Mulligan ─────────────────────────────────────────────────────────────
    val mulliganTitle: String by values
    val mulliganSubtitle: String by values
    val mulliganConfirm: String by values
    val mulliganYouFirst: String by values
    val mulliganOpponentFirst: String by values
    val mulliganWaitingOpponent: String by values
    val mulliganWaitingOpponentTimer: String by values   // formát s %d (zbývající sekundy soupeře)
    val mulliganInstruction: String by values
    val mulliganSelected: String by values       // formát s %d (počet vybraných karet)
    val mulliganPlayNoSwap: String by values
    val mulliganSwap: String by values

    // ── Změna limitů kopií ───────────────────────────────────────────────────
    val limitChangeTitle: String by values
    val limitChangeSubtitle: String by values
    val limitChangeNewLimit: String by values    // formát s %d (nový limit kopií)
    val limitChangeDust: String by values        // formát s %d (získaný prach)
    val limitChangeDecks: String by values       // formát s %d (karet odebráno z balíčků)
    val limitChangeConfirm: String by values

    // ── Profile ──────────────────────────────────────────────────────────────
    val profileTitle: String by values
    val profileWins: String by values
    val profileLosses: String by values
    val profileGames: String by values
    val profileLevel: String by values             // %d
    val profileGold: String by values
    val profileGems: String by values
    val profilePlayed: String by values
    val profileUnlockAll: String by values
    val profileUnlockCampaign: String by values
    val profileSectionAvatar: String by values
    val profileSectionCastle: String by values
    val profileSectionWall: String by values
    val profileSectionCardBack: String by values
    val profileSectionAbilities: String by values
    val profileActiveCount: String by values       // %d / %d
    val profileSectionCosmetics: String by values
    val profileCosmeticsSoon: String by values
    val profileActive: String by values
    val toggleOn: String by values
    val toggleOff: String by values
    val slotFull: String by values
    val castleClassic: String by values
    val castleStone: String by values
    val castleDark: String by values
    val castleOutlawCamp: String by values
    val castleVariant: String by values
    val wallClassic: String by values
    val wallVariant: String by values
    val cardBackBasic: String by values
    val cardBackStyle2: String by values
    val cardBackStyle3: String by values
    val questsTitle: String by values
    val questsReset: String by values
    val questClaim: String by values
    val questWinGames: String by values      // %d
    val questWinOnline: String by values     // %d
    val questPlayCards: String by values     // %d
    val questDealDamage: String by values    // %d
    val questWinCampaign: String by values   // %d
    val questCompletedNotif: String by values   // toast vpravo dole při dokončení questu

    // ── Shop ─────────────────────────────────────────────────────────────────
    val shopTitle: String by values
    val shopBuy: String by values
    val shopDust: String by values

    // ── Online ───────────────────────────────────────────────────────────────
    val onlineConnecting: String by values
    val onlineWaiting: String by values
    val onlineDisconnected: String by values

    // ── Rarities ─────────────────────────────────────────────────────────────
    val rarityCommon: String by values
    val rarityRare: String by values
    val rarityEpic: String by values
    val rarityLegendary: String by values

    // ── Card types ───────────────────────────────────────────────────────────
    val typeAttack: String by values
    val typeBuild: String by values
    val typeMagic: String by values
    val typeChaos: String by values
    val typeMines: String by values
    val typeDecision: String by values
    val typeDraw: String by values

    // ── Resource names (battlefield HUD) ───────────────────────────────────────
    val resMagic: String by values
    val resAttack: String by values
    val resStone: String by values
    val resChaos: String by values

    // ── Game log feed (bottom-right) ───────────────────────────────────────────
    // Card-event row: actor + verb
    val logActorPlayer: String by values
    val logActorAi: String by values
    val logVerbPlayed: String by values
    val logVerbDiscarded: String by values
    val logVerbBurned: String by values
    val logVerbStolen: String by values
    // System events (some take %s = card/resource, %d = amount — sequential order)
    val logBurnedFromOppDeck: String by values
    val logChose: String by values
    val logTookFromDiscard: String by values
    val logCopiedFromDeck: String by values
    val logDrewFromDeck: String by values
    val logChoseMine: String by values
    val logJoker: String by values
    val logStoleFromHand: String by values
    val logChoseResource: String by values
    val logPlayerFirst: String by values
    val logAiFirst: String by values
    val logNotEnough: String by values
    val logConditionNotMet: String by values
    val logReplication: String by values
    val logDiscardEffectTriggered: String by values   // formát: %s (jméno karty)
    val logPlayerEndTurn: String by values
    val logPlayerSkip: String by values
    val logAiDiscardFromDeck: String by values
    val logAiJoker: String by values
    val logAiStoleFromHand: String by values
    val logAiChoseResource: String by values
    val logAiWaited: String by values
    val logBothPassedEmpty: String by values
    val logBothNoCards: String by values
    // Trap-on-draw message: "💥 <who> <card>! <effect>"
    val logTrapDrewYou: String by values
    val logTrapDrewAi: String by values
    val logTrapCastle: String by values
    val logTrapWall: String by values
    val logTrapHp: String by values
    val logTrapTriggered: String by values
    val backShort: String by values
    val opponentDefault: String by values
    val mpQuickMatch: String by values
    val mpSuperRandom: String by values
    val mpLeaderboard: String by values
    val mpDisconnect: String by values
    val mpModeSuperRandom: String by values
    val mpModeQuick: String by values
    val mpDeckLabel: String by values
    val mpQueue: String by values
    val mpGamesSuffix: String by values
    val mpConnectHint: String by values
    val mpNickname: String by values
    val mpConnect: String by values
    val mpConnecting: String by values
    val mpCancel: String by values
    val mpSearching: String by values
    val mpInQueue: String by values
    val mpBackPlain: String by values
    val mpOpponentFound: String by values
    val mpPreparing: String by values
    val mpConnectionError: String by values
    val mpRetry: String by values
    val errUnknown: String by values
    val errNetAbort: String by values
    val errRefused: String by values
    val errNoHost: String by values
    val errNotFound: String by values
    val errTimeout: String by values
    val errReset: String by values
    val errUnreachable: String by values
    val errRejected: String by values
    val errUnknownConn: String by values
    val lobbyDeckReady: String by values
    val lobbyDeckRandom: String by values
    val lobbyEnterNick: String by values
    val lobbySearchingSuperRandom: String by values
    val lobbyDeckNot30: String by values
    val lobbyConnectFailed: String by values
    val lobbyConnectionLost: String by values
    val lobbyConnected: String by values
    val lobbyInQueue: String by values
    val lobbyOpponentFoundPreparing: String by values
    val lobbyOpponentLeftWin: String by values
    val lobbyOutdated: String by values
    val shopPacks: String by values
    val shopPackInfo: String by values
    val shopBuyPack: String by values
    val shopCanBuy: String by values
    val shopEarnGold: String by values
    val shopPackOpened: String by values
    val shopTapToReveal: String by values
    val shopDuplicates: String by values
    val shopFinish: String by values
    val lbLoadFailed: String by values
    val lbTitle: String by values
    val lbTotalPlayers: String by values
    val lbModeSuperRandom: String by values
    val lbLoading: String by values
    val lbRetry: String by values
    val lbEmpty: String by values
    val lbPlayer: String by values
    val stay: String by values
    val leave: String by values
    val surrender: String by values
    val surrenderQ: String by values
    val close: String by values
    val closeX: String by values
    val tapToClose: String by values
    val backToMenuCaps: String by values
    val roundN: String by values
    val opponentAcc: String by values
    val statWinShort: String by values
    val statLossShort: String by values
    val mpStats: String by values
    val leaveGameQ: String by values
    val leaveGameMsg: String by values
    val onlineSurrenderMsg: String by values
    val onlineOppLeft: String by values
    val onlineWaitReconnect: String by values
    val onlineConnLost: String by values
    val onlineReconnecting: String by values
    val onlineYouFirst: String by values
    val onlineOppFirst: String by values
    val onlineGameOver: String by values
    val onlineDraw: String by values
    val onlineDrawEqual: String by values
    val onlineVictory: String by values
    val onlineDefeat: String by values
    val onlineYouBeat: String by values
    val onlineWinnerWon: String by values
    val onlineBackToLobby: String by values
    val lostCardsTitle: String by values
    val lostCardsSubtitle: String by values
    val lostCardsEmpty: String by values
    val lostCardsButton: String by values
    val badgeStolen: String by values
    val badgeBurned: String by values
    val inspectGameCaps: String by values
    val handCount: String by values
    val actionWait: String by values
    val logGameStarts: String by values
    val arenaTitleDraft: String by values
    val arenaPickOne: String by values
    val arenaPick: String by values
    val arenaDeckComposition: String by values
    val arenaEffects: String by values
    val arenaRecentPicks: String by values
    val arenaEnded: String by values
    val arenaWinsWord: String by values
    val arenaRank0: String by values
    val arenaRank1: String by values
    val arenaRank2: String by values
    val arenaRank3: String by values
    val arenaRank4: String by values
    val arenaWinsCount: String by values
    val arenaNextBattle: String by values
    val arenaEnd: String by values
    val arenaYouLost: String by values
    val arenaVictory: String by values
    val arenaDraw: String by values
    val resultYourCastleDestroyed: String by values
    val resultEnemyCastleBuilt: String by values
    val resultDrawEqual: String by values
    val campaignPickHint: String by values
    val campaignCleared: String by values
    val campaignLocked: String by values
    val campaignDefeated: String by values
    val campaignOppLocked: String by values
    val campaignFight: String by values
    val profileNamePrompt: String by values
    val profileNameHint: String by values
    val profileEnterGame: String by values
    val back2: String by values
    val rewardOnlineWin: String by values
    val rewardWin: String by values
    val rewardLoss: String by values
    val dbResourceLabel: String by values
    val dbCraft: String by values
    val dbManaCurve: String by values
    val rogueBuildDeck: String by values
    val rogueRarityLabel: String by values
    val rogueCostLabel: String by values
    val rogueDeckTitle: String by values
    val rogueSavedDecks: String by values
    val rogueCardsCount: String by values
    val rogueBudget: String by values
    val rogueDeckEmpty: String by values
    val rogueFillDeck: String by values
    val rogueStartRun: String by values
    val rogueDeckNotReady: String by values
    val rogueVictory: String by values
    val rogueNext: String by values
    val roguePickRequired: String by values
    val rogueAdd: String by values
    val roguePickExtra: String by values
    val rogueNewMine: String by values
    val rogueSkip: String by values
    val rogueSurrenderMsg: String by values
    val rogueYourDeck: String by values
    val rogueRunComplete: String by values
    val rogueRunOver: String by values
    val rogueAllBattles: String by values
    val rogueCastleFell: String by values
    val rogueBattlesWon: String by values
    val rogueBattleLabel: String by values
    val rogueActTitles: String by values
    val rogueEnemiesAct1: String by values
    val rogueEnemiesAct2: String by values
    val rogueEnemiesAct3: String by values
    val replayWon: String by values
    val replayLost: String by values
    val deckStarter: String by values
    val presetAttacker: String by values
    val presetMage: String by values
    val presetDefender: String by values
    val presetDefender2: String by values
    val presetCardsmith: String by values
    val presetSaboteur: String by values
}

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
