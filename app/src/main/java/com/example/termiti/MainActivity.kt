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

private enum class Screen { MENU, GAME, DECK_BUILDER, ARENA, MP_SELECT, LOCAL_MP, ONLINE_MP }

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()
    private val multiVm: MultiplayerViewModel by viewModels {
        MultiplayerViewModel.factory(
            allCards         = viewModel.allCards,
            decks            = viewModel.decks.toList(),
            activeDeckIndex  = viewModel.activeDeckIndex.value
        )
    }
    private val onlineLobbyVm: OnlineLobbyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        hideSystemUI()
        setContent {
            TermitiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf(Screen.MENU) }
                    val arenaPhase by viewModel.arenaPhase
                    val arenaWins  by viewModel.arenaWins

                    when (screen) {
                        Screen.MENU -> MenuScreen(
                            onStart       = { viewModel.restartGame(); screen = Screen.GAME },
                            onBuildDeck   = { screen = Screen.DECK_BUILDER },
                            onArena       = { viewModel.startArena(); screen = Screen.ARENA },
                            onMultiplayer = { screen = Screen.MP_SELECT },
                            onExit        = { finish() }
                        )
                        Screen.GAME -> GameScreen(
                            viewModel    = viewModel,
                            onBackToMenu = { screen = Screen.MENU }
                        )
                        Screen.DECK_BUILDER -> DeckBuilderScreen(
                            viewModel = viewModel,
                            onBack    = { screen = Screen.MENU }
                        )
                        // ── Výběr módu multiplayer ────────────────────────
                        Screen.MP_SELECT -> MpSelectScreen(
                            onOnline = { screen = Screen.ONLINE_MP },
                            onLocal  = { screen = Screen.LOCAL_MP },
                            onBack   = { screen = Screen.MENU }
                        )
                        // ── Lokální WiFi multiplayer (původní) ────────────
                        Screen.LOCAL_MP -> MultiplayerScreen(
                            vm     = multiVm,
                            onBack = { screen = Screen.MP_SELECT }
                        )
                        // ── Online multiplayer (lobby server) ─────────────
                        Screen.ONLINE_MP -> OnlineMpScreen(
                            vm     = onlineLobbyVm,
                            onBack = { screen = Screen.MP_SELECT }
                        )
                        Screen.ARENA -> when (arenaPhase) {
                            ArenaPhase.DRAFT -> ArenaDraftScreen(
                                viewModel = viewModel,
                                onBack    = { viewModel.exitArena(); screen = Screen.MENU }
                            )
                            ArenaPhase.BATTLE -> GameScreen(
                                viewModel    = viewModel,
                                onBackToMenu = { viewModel.exitArena(); screen = Screen.MENU },
                                isArena      = true,
                                arenaWins    = arenaWins,
                                onArenaWin   = { viewModel.onArenaWin() },
                                onArenaLose  = { viewModel.onArenaLose() }
                            )
                            ArenaPhase.ENDED -> ArenaEndScreen(
                                wins   = arenaWins,
                                onBack = { viewModel.exitArena(); screen = Screen.MENU }
                            )
                            null -> { screen = Screen.MENU }
                        }
                    }
                }
            }
        }
    }

    // Znovu skryj lišty, když se aplikace vrátí do popředí
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
