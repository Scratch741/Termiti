package com.example.termiti

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Herní nastavení mimo zvuk a jazyk (ukládá se do stejného souboru „termiti_settings“
 * jako hlasitosti v [SoundManager]).
 */
object GameSettings {
    private const val KEY_DRAG_TO_PLAY = "drag_to_play"
    private const val KEY_ARENA_LAYOUT = "arena_layout"
    private const val KEY_BADGES_LEFT  = "card_badges_left"
    private var prefs: SharedPreferences? = null

    /**
     * Způsob hraní karet z ruky.
     * false (výchozí) = klepnutím zahrát, tahem nahoru zahodit.
     * true = kartu přetáhnout nad bojiště a pustit (zahrát), zahodit tahem dolů za spodní okraj.
     */
    var dragToPlay by mutableStateOf(false)
        private set

    /**
     * TEST (2026-11-24): rozložení bojiště „Aréna“ (návrh design/ui_redesign/bojiste2/navrh_A2.png) –
     * malba přes celou obrazovku, rohové panely hráčů, ruka v oblouku. Jen offline hra mimo tutoriál.
     */
    var arenaLayout by mutableStateOf(false)
        private set

    /**
     * Ikony stavu na kartě (kladívko = vytvořená, combo, podmínka, lebka) vlevo místo vpravo.
     * V ruce kartu zprava překrývá další karta, vlevo zůstanou vidět. Výchozí vpravo.
     */
    var cardBadgesLeft by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("termiti_settings", Context.MODE_PRIVATE)
        dragToPlay = prefs!!.getBoolean(KEY_DRAG_TO_PLAY, false)
        arenaLayout = prefs!!.getBoolean(KEY_ARENA_LAYOUT, false)
        cardBadgesLeft = prefs!!.getBoolean(KEY_BADGES_LEFT, false)
    }

    fun updateArenaLayout(value: Boolean) {
        arenaLayout = value
        prefs?.edit()?.putBoolean(KEY_ARENA_LAYOUT, value)?.apply()
    }

    fun updateCardBadgesLeft(value: Boolean) {
        cardBadgesLeft = value
        prefs?.edit()?.putBoolean(KEY_BADGES_LEFT, value)?.apply()
    }

    fun updateDragToPlay(value: Boolean) {
        dragToPlay = value
        prefs?.edit()?.putBoolean(KEY_DRAG_TO_PLAY, value)?.apply()
    }
}
