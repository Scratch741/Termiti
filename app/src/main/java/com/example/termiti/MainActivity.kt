package com.example.termiti

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.getValue
import com.example.termiti.ui.theme.TermitiTheme

private enum class Screen {
    PROFILE_SETUP,
    MENU, PLAY_MENU, GAME, DECK_BUILDER, ARENA, ROGUELIKE, ONLINE_MP, SETTINGS, PROFILE,
    CAMPAIGN_MAP, CAMPAIGN_LOCATION, CAMPAIGN_GAME, CAMPAIGN_RESULT,
    SHOP, LEADERBOARD
}

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()
    private val onlineLobbyVm: OnlineLobbyViewModel by viewModels {
        OnlineLobbyViewModel.Factory(allCards = viewModel.allCards, context = applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CrashReporter.init(this)
        SoundManager.initSounds(this)
        SoundManager.startBackgroundMusic(this)
        CardRepository.init(this)
        PlayerProfileManager.init(this)
        CampaignManager.init(this)
        QuestManager.init(this)
        LanguageManager.init(this)
        RogueSaveManager.init(this)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            TermitiTheme {
                // ── Language provider ─────────────────────────────────────────
                val langPack by LanguageManager.currentPackState
                val strings  = langPack?.strings ?: LanguageManager.currentStrings

                androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides strings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                  DesignFrame {
                    val initialScreen = if (PlayerProfileManager.isFirstLaunch())
                        Screen.PROFILE_SETUP else Screen.MENU
                    var screen      by remember { mutableStateOf(initialScreen) }
                    var gameRandom  by remember { mutableStateOf(false) }
                    var gameSuperRandom by remember { mutableStateOf(false) }
                    val arenaPhase by viewModel.arenaPhase
                    val arenaWins  by viewModel.arenaWins
                    val roguePhase by viewModel.roguePhase

                    // ── Migrace limitů rarities ───────────────────────────────
                    val migrationResult by viewModel.rarityMigrationResult
                    migrationResult?.let { result ->
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { viewModel.rarityMigrationResult.value = null },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = { viewModel.rarityMigrationResult.value = null }
                                ) { androidx.compose.material3.Text("OK") }
                            },
                            title = { androidx.compose.material3.Text("Aktualizace balíčků") },
                            text  = {
                                val lines = buildString {
                                    appendLine("Limity kopií karet byly sníženy na 3/2/2/1.")
                                    if (result.deckCardsRemoved > 0)
                                        appendLine("• Z balíčků odebráno ${result.deckCardsRemoved} přebytečných karet.")
                                    if (result.dustGained > 0)
                                        appendLine("• Za přebytečné kopie v kolekci získáváš +${result.dustGained} prachu ✨")
                                }
                                androidx.compose.material3.Text(lines.trim())
                            }
                        )
                    }

                    // ── Kampaň ────────────────────────────────────────────────
                    var campaignLocation by remember { mutableStateOf<CampaignLocation?>(null) }
                    val campaignOpponent by viewModel.activeCampaignOpponent
                    var campaignPlayerWon by remember { mutableStateOf(false) }

                    // Sleduj aktuální obrazovku pro crash reporting
                    CrashReporter.lastScreen = screen.name

                    when (screen) {

                        // ── Profil ────────────────────────────────────────────
                        Screen.PROFILE_SETUP -> ProfileSetupScreen(
                            onDone = { viewModel.grantStarterDeck(); screen = Screen.MENU }
                        )

                        // ── Hlavní menu ───────────────────────────────────────
                        Screen.MENU -> MenuScreen(
                            onPlay        = { screen = Screen.PLAY_MENU },
                            onBuildDeck   = { screen = Screen.DECK_BUILDER },
                            onMultiplayer = { screen = Screen.ONLINE_MP },
                            onProfile     = { screen = Screen.PROFILE },
                            onShop        = { screen = Screen.SHOP },
                            onSettings    = { screen = Screen.SETTINGS },
                            onExit        = { finish() }
                        )

                        Screen.PROFILE -> ProfileScreen(
                            onBack = { screen = Screen.MENU }
                        )

                        // ── Výběr herního módu ────────────────────────────────
                        Screen.PLAY_MENU -> PlayMenuScreen(
                            onOwnDeck     = { gameRandom = false; gameSuperRandom = false; viewModel.restartGame(randomDeck = false);                    screen = Screen.GAME },
                            onSuperRandom = { gameRandom = false; gameSuperRandom = true;  viewModel.restartGame(randomDeck = false, superRandom = true); screen = Screen.GAME },
                            onArena       = { viewModel.startArena(); screen = Screen.ARENA },
                            onRoguelike   = {
                                when {
                                    viewModel.roguePhase.value != null -> { /* run běží v paměti → jen zobraz */ }
                                    viewModel.hasRogueSave()           -> viewModel.resumeRoguelike()  // přežil zabití procesu
                                    else                               -> viewModel.startRoguelike()   // nový run
                                }
                                screen = Screen.ROGUELIKE
                            },
                            onCampaign    = { screen = Screen.CAMPAIGN_MAP },
                            onBack        = { screen = Screen.MENU },
                            onShop        = { screen = Screen.SHOP },
                            onSettings    = { screen = Screen.SETTINGS }
                        )

                        Screen.SHOP -> ShopScreen(
                            allCards = viewModel.allCards,
                            onBack   = { screen = Screen.MENU }
                        )

                        Screen.SETTINGS -> SettingsScreen(
                            onBack = { screen = Screen.MENU }
                        )

                        // ── Offline hra ───────────────────────────────────────
                        Screen.GAME -> GameScreen(
                            viewModel    = viewModel,
                            onBackToMenu = { screen = Screen.MENU },
                            onGameEnd    = { win ->
                                PlayerProfileManager.recordGameResult(win = win, online = false)
                            },
                            randomDeck   = gameRandom,
                            superRandom  = gameSuperRandom
                        )

                        // ── Deck builder ──────────────────────────────────────
                        Screen.DECK_BUILDER -> DeckBuilderScreen(
                            viewModel = viewModel,
                            onBack    = { screen = Screen.MENU }
                        )

                        // ── Multiplayer ───────────────────────────────────────
                        Screen.ONLINE_MP -> OnlineMpScreen(
                            vm            = onlineLobbyVm,
                            decks         = viewModel.decks,
                            onBack        = { screen = Screen.MENU },
                            onLeaderboard = { screen = Screen.LEADERBOARD }
                        )

                        Screen.LEADERBOARD -> LeaderboardScreen(
                            onBack = { screen = Screen.ONLINE_MP }
                        )

                        // ── Kampaň ───────────────────────────────────────────
                        Screen.CAMPAIGN_MAP -> CampaignMapScreen(
                            onLocationSelected = { loc ->
                                campaignLocation = loc
                                screen = Screen.CAMPAIGN_LOCATION
                            },
                            onBack = { screen = Screen.PLAY_MENU }
                        )

                        Screen.CAMPAIGN_LOCATION -> {
                            val loc = campaignLocation
                            if (loc == null) { screen = Screen.CAMPAIGN_MAP } else {
                                CampaignLocationScreen(
                                    location = loc,
                                    onOpponentSelected = { opp ->
                                        viewModel.startCampaignBattle(opp)
                                        screen = Screen.CAMPAIGN_GAME
                                    },
                                    onBack = { screen = Screen.CAMPAIGN_MAP }
                                )
                            }
                        }

                        Screen.CAMPAIGN_GAME -> GameScreen(
                            viewModel    = viewModel,
                            onBackToMenu = { screen = Screen.CAMPAIGN_LOCATION },
                            onGameEnd    = { win ->
                                // campaign = true → žádná obecná herní odměna, jen statistiky + questy
                                PlayerProfileManager.recordGameResult(win = win, online = false, campaign = true)
                                if (win) QuestManager.onCampaignWin()
                                campaignPlayerWon = win
                                screen = Screen.CAMPAIGN_RESULT
                            }
                        )

                        Screen.CAMPAIGN_RESULT -> {
                            val opp = campaignOpponent
                            if (opp == null) { screen = Screen.CAMPAIGN_MAP } else {
                                CampaignResultScreen(
                                    opponent        = opp,
                                    playerWon       = campaignPlayerWon,
                                    onRetry         = {
                                        viewModel.startCampaignBattle(opp)
                                        screen = Screen.CAMPAIGN_GAME
                                    },
                                    onBackToLocation = { screen = Screen.CAMPAIGN_LOCATION },
                                    onBackToMap      = { screen = Screen.CAMPAIGN_MAP }
                                )
                            }
                        }

                        // ── Aréna ─────────────────────────────────────────────
                        Screen.ARENA -> when (arenaPhase) {
                            ArenaPhase.DRAFT -> ArenaDraftScreen(
                                viewModel = viewModel,
                                onBack    = { viewModel.exitArena(); screen = Screen.PLAY_MENU }
                            )
                            ArenaPhase.BATTLE -> GameScreen(
                                viewModel    = viewModel,
                                onBackToMenu = { viewModel.exitArena(); screen = Screen.PLAY_MENU },
                                isArena      = true,
                                arenaWins    = arenaWins,
                                onArenaWin   = { viewModel.onArenaWin() },
                                onArenaLose  = { viewModel.onArenaLose() },
                                onGameEnd    = { win ->
                                    PlayerProfileManager.recordGameResult(win = win, online = false)
                                }
                            )
                            ArenaPhase.ENDED -> ArenaEndScreen(
                                wins   = arenaWins,
                                onBack = { viewModel.exitArena(); screen = Screen.PLAY_MENU }
                            )
                            null -> { screen = Screen.PLAY_MENU }
                        }

                        // ── Roguelike ─────────────────────────────────────────
                        Screen.ROGUELIKE -> when (roguePhase) {
                            RoguePhase.DRAFT -> RogueDraftScreen(
                                viewModel = viewModel,
                                onBack    = { viewModel.exitRoguelike(); screen = Screen.PLAY_MENU }
                            )
                            RoguePhase.BATTLE -> GameScreen(
                                viewModel    = viewModel,
                                onBackToMenu = { viewModel.exitRoguelike(); screen = Screen.PLAY_MENU },
                                onGameEnd    = { win ->
                                    PlayerProfileManager.recordGameResult(win = win, online = false)
                                    viewModel.onRogueBattleEnd(win)
                                }
                            )
                            RoguePhase.REWARD -> RogueRewardScreen(
                                viewModel = viewModel,
                                onExit    = { viewModel.exitRoguelike(); screen = Screen.PLAY_MENU },
                                // Menu = jen odejít, run zůstává rozehraný (v paměti + na disku
                                // díky auto-save) – "Vzdát se" ho oproti tomu natvrdo zahodí.
                                onMenu    = { screen = Screen.PLAY_MENU }
                            )
                            RoguePhase.ENDED -> RogueEndScreen(
                                viewModel = viewModel,
                                onBack    = { viewModel.exitRoguelike(); screen = Screen.PLAY_MENU }
                            )
                            null -> { screen = Screen.PLAY_MENU }
                        }
                    }
                    // Toast overlay – musí být poslední, aby byl nad vším ostatním
                    RewardToastOverlay()
                  } // konec DesignFrame
                }
                } // konec CompositionLocalProvider
            }
        }
    }

    override fun onResume()  { super.onResume();  SoundManager.resumeBackgroundMusic() }
    override fun onPause()   { super.onPause();   SoundManager.pauseBackgroundMusic() }
    override fun onDestroy() { super.onDestroy(); SoundManager.stopBackgroundMusic(); SoundManager.releaseSounds() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

// ── Designový rám ─────────────────────────────────────────────────────────────
// Celé UI je laděné na telefon na šířku (Samsung S23 = 2340×1080, 19,5:9).
// Rám dělá dvě věci:
//
// 1. POMĚR STRAN – obsah drží referenční poměr 19,5:9; na zařízeních s jiným
//    poměrem (tablety ~16:10) se vycentruje s pruhy → rozložení je identické.
//
// 2. MĚŘÍTKO (virtuální rozlišení) – uvnitř rámu se přepíše LocalDensity tak,
//    aby výška rámu byla vždy DESIGN_HEIGHT_DP (výška S23 na šířku v dp).
//    Bez toho by na tabletu byl rám v dp jednotkách větší (nižší density
//    vůči fyzické velikosti) a všechny prvky s pevnou dp velikostí (karty,
//    hrad, mulligan dialog, texty v sp) by vypadaly zmenšené. Takto se každý
//    dp/sp prvek škáluje přesně úměrně = hra vypadá na tabletu jako zvětšený
//    telefon. Na S23 vychází škála 1:1 (žádná změna).
@Composable
private fun DesignFrame(content: @Composable () -> Unit) {
    val designAR = 2340f / 1080f   // referenční poměr stran (S23 na šířku)
    val designHeightDp = 411.4f    // 1080 px / density 2.625 = výška S23 v dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val screenAR = maxWidth / maxHeight
        val frameMod =
            if (screenAR > designAR) Modifier.fillMaxHeight().aspectRatio(designAR)
            else                     Modifier.fillMaxWidth().aspectRatio(designAR)
        val density = LocalDensity.current
        val frameHeightPx = with(density) {
            (if (screenAR > designAR) maxHeight else maxWidth / designAR).toPx()
        }
        // fontScale = 1f: zamkne i velikost písma. Systémové nastavení "větší písmo"
        // by jinak zvětšilo sp texty vůči dp kartám → texty přetečou (na kartách se
        // 4 řádky popisu vejdou jen při referenčním měřítku písma).
        val scaledDensity = Density(
            density   = frameHeightPx / designHeightDp,
            fontScale = 1f
        )
        // LocalGameDensity: dialogy (vlastní okna) si LocalDensity resetují na
        // systémovou → GameDialog si z něj herní škálování obnoví.
        CompositionLocalProvider(
            LocalDensity     provides scaledDensity,
            LocalGameDensity provides scaledDensity
        ) {
            Box(modifier = frameMod) { content() }
        }
    }
}
