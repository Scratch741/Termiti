package com.example.termiti

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.termiti.ui.theme.TermitiTheme

private enum class Screen {
    PROFILE_SETUP,
    MENU, PLAY_MENU, GAME, DECK_BUILDER, ARENA, MP_SELECT, LOCAL_MP, ONLINE_MP, SETTINGS, PROFILE,
    CAMPAIGN_MAP, CAMPAIGN_LOCATION, CAMPAIGN_GAME, CAMPAIGN_RESULT,
    SHOP, LEADERBOARD
}

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()
    private val multiVm: MultiplayerViewModel by viewModels {
        MultiplayerViewModel.factory(
            allCards        = viewModel.allCards,
            decks           = viewModel.decks.toList(),
            activeDeckIndex = viewModel.activeDeckIndex.value
        )
    }
    private val onlineLobbyVm: OnlineLobbyViewModel by viewModels {
        OnlineLobbyViewModel.Factory(allCards = viewModel.allCards, context = applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SoundManager.initSounds(this)
        SoundManager.startBackgroundMusic(this)
        CardRepository.init(this)
        PlayerProfileManager.init(this)
        CampaignManager.init(this)
        QuestManager.init(this)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            TermitiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val initialScreen = if (PlayerProfileManager.isFirstLaunch())
                        Screen.PROFILE_SETUP else Screen.MENU
                    var screen      by remember { mutableStateOf(initialScreen) }
                    var gameRandom  by remember { mutableStateOf(false) }
                    var gameSuperRandom by remember { mutableStateOf(false) }
                    val arenaPhase by viewModel.arenaPhase
                    val arenaWins  by viewModel.arenaWins

                    // ── Kampaň ────────────────────────────────────────────────
                    var campaignLocation by remember { mutableStateOf<CampaignLocation?>(null) }
                    val campaignOpponent by viewModel.activeCampaignOpponent
                    var campaignPlayerWon by remember { mutableStateOf(false) }

                    when (screen) {

                        // ── Profil ────────────────────────────────────────────
                        Screen.PROFILE_SETUP -> ProfileSetupScreen(
                            onDone = { viewModel.grantStarterDeck(); screen = Screen.MENU }
                        )

                        // ── Hlavní menu ───────────────────────────────────────
                        Screen.MENU -> MenuScreen(
                            onPlay        = { screen = Screen.PLAY_MENU },
                            onBuildDeck   = { screen = Screen.DECK_BUILDER },
                            onMultiplayer = { screen = Screen.MP_SELECT },
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
                            onRandomDeck  = { gameRandom = true;  gameSuperRandom = false; viewModel.restartGame(randomDeck = true);                     screen = Screen.GAME },
                            onSuperRandom = { gameRandom = false; gameSuperRandom = true;  viewModel.restartGame(randomDeck = false, superRandom = true); screen = Screen.GAME },
                            onArena       = { viewModel.startArena(); screen = Screen.ARENA },
                            onCampaign    = { screen = Screen.CAMPAIGN_MAP },
                            onBack        = { screen = Screen.MENU }
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
                        Screen.MP_SELECT -> MpSelectScreen(
                            onOnline = { screen = Screen.ONLINE_MP },
                            onLocal  = { screen = Screen.LOCAL_MP },
                            onBack   = { screen = Screen.MENU }
                        )
                        Screen.LOCAL_MP -> MultiplayerScreen(
                            vm     = multiVm,
                            onBack = { screen = Screen.MP_SELECT }
                        )
                        Screen.ONLINE_MP -> OnlineMpScreen(
                            vm            = onlineLobbyVm,
                            decks         = viewModel.decks,
                            onBack        = { screen = Screen.MP_SELECT },
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
                    }
                    // Toast overlay – musí být poslední, aby byl nad vším ostatním
                    RewardToastOverlay()
                }
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
