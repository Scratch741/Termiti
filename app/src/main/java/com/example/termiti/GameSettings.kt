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
    private var prefs: SharedPreferences? = null

    /**
     * Způsob hraní karet z ruky.
     * false (výchozí) = klepnutím zahrát, tahem nahoru zahodit.
     * true = kartu přetáhnout nad bojiště a pustit (zahrát), zahodit tahem dolů za spodní okraj.
     */
    var dragToPlay by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("termiti_settings", Context.MODE_PRIVATE)
        dragToPlay = prefs!!.getBoolean(KEY_DRAG_TO_PLAY, false)
    }

    fun updateDragToPlay(value: Boolean) {
        dragToPlay = value
        prefs?.edit()?.putBoolean(KEY_DRAG_TO_PLAY, value)?.apply()
    }
}
